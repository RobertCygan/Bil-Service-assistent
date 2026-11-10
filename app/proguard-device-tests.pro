# Rules for the device-test package itself (only with -PreleaseTests): the tests are not shrunk.
-keep class ** { *; }
-dontwarn javax.lang.model.**
