package com.ccms.costing;

import com.ccms.hierarchy.Location;
import com.ccms.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Posts non-labour costs (purchases, rentals, sub-contract bills, site expenses) to the
 * append-only ledger. A changed document is re-posted: open rows reversed, new rows added.
 */
@Component
@RequiredArgsConstructor
public class LedgerPoster {

    private final CostEntryRepository ledger;

    public record Posting(CostEntry.SourceType type, Long sourceId, LocalDate date, Location location,
                          Long workItemId, Long partyId, Long materialId, BigDecimal amount) {
    }

    public void post(Posting p) {
        if (p.amount() == null || p.amount().signum() == 0) {
            return;
        }
        CostEntry e = new CostEntry();
        e.setSourceType(p.type());
        e.setSourceId(p.sourceId());
        e.setEntryDate(p.date());
        e.setSiteId(p.location().siteId());
        e.setBuildingId(p.location().buildingId());
        e.setFloorId(p.location().floorId());
        e.setUnitId(p.location().unitId());
        e.setWorkItemId(p.workItemId());
        e.setPartyId(p.partyId());
        e.setMaterialId(p.materialId());
        e.setAmount(p.amount());
        e.setCreatedBy(CurrentUser.userIdOrNull());
        ledger.save(e);
    }

    /** Cancels everything currently posted for a source document. */
    public void reverse(CostEntry.SourceType type, Long sourceId) {
        Long userId = CurrentUser.userIdOrNull();
        for (CostEntry e : ledger.openEntries(type, sourceId)) {
            ledger.save(e.reversal(userId));
        }
    }
}
