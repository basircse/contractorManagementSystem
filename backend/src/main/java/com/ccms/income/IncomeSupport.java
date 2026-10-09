package com.ccms.income;

import com.ccms.catalog.WorkItemController.WorkItemRepository;
import com.ccms.common.AmountLine;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.hierarchy.Client;
import com.ccms.hierarchy.HierarchyRepositories.ClientRepository;
import com.ccms.hierarchy.HierarchyRepositories.SiteRepository;
import com.ccms.hierarchy.HierarchyService;
import com.ccms.hierarchy.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared helpers for quotations, work orders and bills. */
@Component
@RequiredArgsConstructor
public class IncomeSupport {

    private final WorkItemRepository workItems;
    private final ClientRepository clients;
    private final SiteRepository sites;
    private final HierarchyService hierarchy;

    /** Copies a request line onto an entity line, computing the amount. */
    public <T extends AmountLine> T fill(T line, int lineNo, LineRequest r) {
        if (r.workItemId() != null) {
            TenantGuard.own(workItems.findById(r.workItemId()), "WorkItem", r.workItemId());
        }
        line.setLineNo(lineNo);
        line.setWorkItemId(r.workItemId());
        line.setDescription(r.description().trim());
        line.setUom(r.uom() == null || r.uom().isBlank() ? null : r.uom().trim().toUpperCase());
        line.setQuantity(r.quantity());
        line.setRate(r.rate());
        line.setAmount(Money.times(r.quantity(), r.rate()));
        return line;
    }

    /** Client must exist; the site (if given) must belong to that client. */
    public void checkClientSite(Long clientId, Long siteId) {
        hierarchy.client(clientId);
        if (siteId != null && !Objects.equals(hierarchy.site(siteId).getClientId(), clientId)) {
            throw new ApiException(ErrorCode.INVALID_LOCATION, "Site does not belong to the client");
        }
    }

    public Map<Long, String> clientNames() {
        return clients.findAll().stream().collect(Collectors.toMap(Client::getId, Client::getName));
    }

    public Map<Long, String> siteNames() {
        return sites.findAll().stream().collect(Collectors.toMap(Site::getId, Site::getName));
    }

    public static <T> Map<Long, T> byId(java.util.Collection<T> rows, Function<T, Long> id) {
        return rows.stream().collect(Collectors.toMap(id, r -> r, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** {@code reason} is a stable key the UI translates (errors.reasons.*). */
    public static ApiException invalidState(String reason, String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message, Map.of("reason", reason));
    }
}
