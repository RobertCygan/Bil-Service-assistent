package app.servicepassport.export

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Złe hasło albo uszkodzony/zmieniony plik — tych dwóch przypadków nie da się rozróżnić. */
public class ExportDecryptionException(message: String) : IOException(message)

/**
 * Opcjonalne szyfrowanie kopii hasłem. Wyłącznie standardowe mechanizmy platformy:
 * klucz z hasła przez PBKDF2-HMAC-SHA256, treść szyfrowana AES-256-GCM.
 *
 * Plik jest szyfrowany w kawałkach po 64 KiB, żeby duże kopie nie musiały mieścić się w pamięci
 * i żeby żaden niezweryfikowany fragment nie trafił do dalszego przetwarzania. Numer kawałka i znacznik
 * „ostatni” są częścią parametru szyfru, więc obcięcie pliku lub zamiana kolejności jest wykrywana.
 *
 * Układ: 8 bajtów znacznika, 4 bajty liczby iteracji, 16 bajtów soli, 7 bajtów losowego prefiksu,
 * potem kawałki (szyfrogram + 16 bajtów znacznika GCM).
 *
 * Zapomnianego hasła nie da się odzyskać — aplikacja musi to powiedzieć użytkownikowi przed eksportem.
 */
public object ExportEncryption {
    /** Zalecenie OWASP dla PBKDF2-HMAC-SHA256. */
    public const val DEFAULT_ITERATIONS: Int = 600_000
    internal const val MIN_ITERATIONS: Int = 10_000
    internal const val MAX_ITERATIONS: Int = 10_000_000
    internal const val CHUNK: Int = 64 * 1024
    private const val TAG_BYTES = 16
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 7
    private val MAGIC = "CSAENC01".toByteArray(Charsets.US_ASCII)
    internal val HEADER_BYTES: Int = MAGIC.size + 4 + SALT_BYTES + PREFIX_BYTES

    /** Czy plik zaczyna się znacznikiem zaszyfrowanej kopii. Wystarczy pierwsze 8 bajtów. */
    public fun isEncrypted(start: ByteArray): Boolean = start.size >= MAGIC.size && start.copyOf(MAGIC.size).contentEquals(MAGIC)

    /** Zwraca strumień, do którego zapisuje się jawną treść. Trzeba go zamknąć — dopiero wtedy powstaje ostatni kawałek. */
    public fun encrypting(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported iteration count" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(iterations).put(salt).put(prefix).array()
        out.write(header)
        return Encrypting(out, key(password, salt, iterations), prefix, header)
    }

    /** Zwraca strumień jawnej treści. Rzuca [ExportDecryptionException] przy złym haśle lub naruszonym pliku. */
    public fun decrypting(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER_BYTES)
        if (readFully(input, header) != HEADER_BYTES || !isEncrypted(header)) throw ExportDecryptionException("Not an encrypted export")
        val buffer = ByteBuffer.wrap(header, MAGIC.size, HEADER_BYTES - MAGIC.size)
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw ExportDecryptionException("Unsupported iteration count")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val prefix = ByteArray(PREFIX_BYTES).also { buffer.get(it) }
        return Decrypting(PushbackInputStream(input, 1), key(password, salt, iterations), prefix, header)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, counter: Int, last: Boolean, header: ByteArray): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(header)
        }
    }

    private fun readFully(input: InputStream, into: ByteArray): Int {
        var read = 0
        while (read < into.size) {
            val n = input.read(into, read, into.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private class Encrypting(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
    ) : OutputStream() {
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var offset = off
            var left = len
            while (left > 0) {
                if (filled == CHUNK) emit(last = false) // pełny kawałek wysyłamy dopiero, gdy wiadomo, że nie jest ostatni
                val n = minOf(left, CHUNK - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n; offset += n; left -= n
            }
        }

        private fun emit(last: Boolean) {
            if (counter == Int.MAX_VALUE) throw IOException("Export too large")
            out.write(cipher(Cipher.ENCRYPT_MODE, key, prefix, counter++, last, header).doFinal(buffer, 0, filled))
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            emit(last = true)
            out.close()
        }
    }

    private class Decrypting(
        private val input: PushbackInputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var position = 0
        private var counter = 0
        private var finished = false

        private fun fill(): Boolean {
            if (position < plain.size) return true
            if (finished) return false
            val block = ByteArray(CHUNK + TAG_BYTES)
            val read = readFully(input, block)
            if (read < TAG_BYTES) throw ExportDecryptionException("Export is truncated")
            val next = input.read()
            val last = next < 0
            if (!last) input.unread(next)
            plain = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, counter++, last, header).doFinal(block, 0, read)
            } catch (_: GeneralSecurityException) {
                throw ExportDecryptionException("Wrong password or damaged export")
            }
            position = 0
            finished = last
            return plain.isNotEmpty() || fill()
        }

        override fun read(): Int = if (fill()) plain[position++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, plain.size - position)
            System.arraycopy(plain, position, b, off, n)
            position += n
            return n
        }

        override fun close() = input.close()
    }
}
