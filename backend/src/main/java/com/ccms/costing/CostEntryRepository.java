package com.ccms.costing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CostEntryRepository extends JpaRepository<CostEntry, Long> {

    /** Original rows of a source document that have not been reversed yet. */
    @Query("""
            select e from CostEntry e
            where e.sourceType = :type and e.sourceId = :sourceId and e.reversalOf is null
              and not exists (select 1 from CostEntry r where r.reversalOf = e.id)""")
    List<CostEntry> openEntries(CostEntry.SourceType type, Long sourceId);

    /** Net amount per source document, e.g. today's wage cost per attendance row. */
    @Query("""
            select e.sourceId, sum(e.amount) from CostEntry e
            where e.sourceType = :type and e.sourceId in :sourceIds
            group by e.sourceId""")
    List<Object[]> netBySource(CostEntry.SourceType type, Collection<Long> sourceIds);
}
