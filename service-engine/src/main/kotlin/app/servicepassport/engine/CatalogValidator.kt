package app.servicepassport.engine

import java.time.LocalDate

public enum class CatalogIssueCode {
    NOT_VERIFIED_ORIGIN, SOURCE_FIELDS_BLANK, VERIFIED_IN_FUTURE, VALIDITY_BEFORE_VERIFICATION,
    EMPTY_SCOPE, EMPTY_RULES, DUPLICATE_PLAN_ID, DUPLICATE_RULE_ID, DUPLICATE_TASK_CODE,
}

public data class CatalogIssue(val planId: String, val ruleId: String?, val code: CatalogIssueCode)

/**
 * Bramka katalogu produkcyjnego: build wydania powinien przerwać się, gdy lista nie jest pusta.
 * Sprawdza kompletność pochodzenia, a nie merytoryczną poprawność interwałów.
 */
public object CatalogValidator {

    public fun productionIssues(catalog: List<ServicePlan>, today: LocalDate): List<CatalogIssue> {
        val issues = mutableListOf<CatalogIssue>()
        catalog.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { issues += CatalogIssue(it, null, CatalogIssueCode.DUPLICATE_PLAN_ID) }

        for (plan in catalog) {
            fun check(source: SourceRef, ruleId: String?) {
                fun add(code: CatalogIssueCode) { issues += CatalogIssue(plan.id, ruleId, code) }
                if (source.origin != DataOrigin.VERIFIED_SOURCE) add(CatalogIssueCode.NOT_VERIFIED_ORIGIN)
                if (listOf(source.id, source.title, source.version, source.usageRights).any { it.isBlank() }) {
                    add(CatalogIssueCode.SOURCE_FIELDS_BLANK)
                }
                if (source.verifiedOn.isAfter(today)) add(CatalogIssueCode.VERIFIED_IN_FUTURE)
                if (source.validUntil?.isBefore(source.verifiedOn) == true) {
                    add(CatalogIssueCode.VALIDITY_BEFORE_VERIFICATION)
                }
            }
            check(plan.source, null)
            if (plan.scope.isEmpty() || plan.scope.values.any { set -> set.isEmpty() || set.any { it.isBlank() } }) {
                issues += CatalogIssue(plan.id, null, CatalogIssueCode.EMPTY_SCOPE)
            }
            if (plan.rules.isEmpty()) issues += CatalogIssue(plan.id, null, CatalogIssueCode.EMPTY_RULES)
            plan.rules.forEach { check(it.source, it.id) }
            plan.rules.groupBy { it.id }.filterValues { it.size > 1 }.keys
                .forEach { issues += CatalogIssue(plan.id, it, CatalogIssueCode.DUPLICATE_RULE_ID) }
            plan.rules.groupBy { it.taskCode }.filterValues { it.size > 1 }.values
                .forEach { issues += CatalogIssue(plan.id, it.first().id, CatalogIssueCode.DUPLICATE_TASK_CODE) }
        }
        return issues
    }
}
