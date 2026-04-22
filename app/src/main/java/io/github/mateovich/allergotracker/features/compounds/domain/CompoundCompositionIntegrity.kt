package io.github.mateovich.allergotracker.features.compounds.domain

data class CompoundChildEdge(
    val parentCompoundId: Long,
    val childCompoundId: Long
)

object CompoundCompositionIntegrity {
    fun childEdgesFromCompounds(compounds: List<ReusableCompound>): List<CompoundChildEdge> {
        return compounds.flatMap { compound ->
            compound.components.mapNotNull { component ->
                component.childCompoundId?.let { childCompoundId ->
                    CompoundChildEdge(
                        parentCompoundId = compound.id,
                        childCompoundId = childCompoundId
                    )
                }
            }
        }
    }

    fun findDuplicateComponentIndexes(
        components: List<Pair<CompoundComponentKind, Long?>>
    ): Set<Int> {
        val seen = linkedSetOf<Pair<CompoundComponentKind, Long?>>()
        val duplicates = linkedSetOf<Int>()
        components.forEachIndexed { index, component ->
            if (!seen.add(component)) {
                duplicates += index
            }
        }
        return duplicates
    }

    fun wouldCreateCycle(
        parentCompoundId: Long,
        childCompoundId: Long,
        childEdges: List<CompoundChildEdge>
    ): Boolean {
        if (parentCompoundId == childCompoundId) {
            return true
        }
        val graph = childEdges.groupBy(CompoundChildEdge::parentCompoundId, CompoundChildEdge::childCompoundId)
        val pending = ArrayDeque<Long>()
        val visited = mutableSetOf<Long>()
        pending.add(childCompoundId)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            if (current == parentCompoundId) {
                return true
            }
            graph[current].orEmpty().forEach(pending::addLast)
        }
        return false
    }

    fun invalidChildCompoundIds(
        parentCompoundId: Long?,
        candidateIds: Iterable<Long>,
        childEdges: List<CompoundChildEdge>
    ): Set<Long> {
        val currentParentId = parentCompoundId ?: return emptySet()
        return candidateIds.filterTo(linkedSetOf()) { candidateId ->
            wouldCreateCycle(currentParentId, candidateId, childEdges)
        }
    }

    fun countRecursiveMergeParents(
        sourceCompoundId: Long,
        destinationCompoundId: Long,
        impactedParentCompoundIds: Iterable<Long>,
        childEdges: List<CompoundChildEdge>
    ): Int {
        val adjustedEdges = childEdges.map { edge ->
            if (edge.childCompoundId == sourceCompoundId) {
                edge.copy(childCompoundId = destinationCompoundId)
            } else {
                edge
            }
        }
        return impactedParentCompoundIds.toSet().count { parentCompoundId ->
            wouldCreateCycle(parentCompoundId, destinationCompoundId, adjustedEdges)
        }
    }
}
