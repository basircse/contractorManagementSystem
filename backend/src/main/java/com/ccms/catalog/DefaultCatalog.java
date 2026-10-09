package com.ccms.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard work items (FR-1.2) seeded for every new contractor. Written with JDBC because
 * the caller is the platform admin, whose Hibernate session is not bound to this tenant.
 */
@Component
@RequiredArgsConstructor
public class DefaultCatalog {

    private record Item(String code, String nameBn, String nameEn, String uom) {
    }

    private static final List<Item> ITEMS = List.of(
            new Item("PILING", "পাইলিং / ফাউন্ডেশন", "Piling / Foundation", "RFT"),
            new Item("COLUMN_CASTING", "কলাম ঢালাই", "Column Casting", "CFT"),
            new Item("SLAB_CASTING", "ফ্লোর / ছাদ ঢালাই", "Floor / Slab / Roof Casting", "CFT"),
            new Item("BRICKWORK", "ইটের গাঁথুনি", "Brickwork / Masonry", "CFT"),
            new Item("PLASTER_INT", "প্লাস্টার (ভিতরে)", "Plastering (Internal)", "SFT"),
            new Item("PLASTER_EXT", "প্লাস্টার (বাহিরে)", "Plastering (External)", "SFT"),
            new Item("SHUTTERING", "সাটারিং ও ফর্মওয়ার্ক", "Shuttering & Formwork", "SFT"),
            new Item("TILES", "টাইলস ও ফ্লোরিং", "Tiles & Flooring", "SFT"),
            new Item("PAINTING", "রং ও ফিনিশিং", "Painting & Finishing", "SFT"),
            new Item("GENERAL", "সাধারণ কাজ", "General Site Work", "LS"));

    private final JdbcTemplate jdbc;

    public void seed(long contractorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (int i = 0; i < ITEMS.size(); i++) {
            Item it = ITEMS.get(i);
            jdbc.update("""
                    INSERT INTO work_item (contractor_id, code, name_bn, name_en, uom, sort_order, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)""",
                    contractorId, it.code(), it.nameBn(), it.nameEn(), it.uom(), (i + 1) * 10, now, now);
        }
    }
}
