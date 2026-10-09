package com.ccms.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard work items (FR-1.2) and materials seeded for every new contractor. Written with JDBC because
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

    private record Mat(String code, String nameBn, String nameEn, String uom, String kind) {
    }

    /** Keep in step with the INSERT at the end of V2__income_and_expenses.sql. */
    private static final List<Mat> MATERIALS = List.of(
            new Mat("CEMENT", "সিমেন্ট", "Cement", "BAG", "CONSUMABLE"),
            new Mat("ROD", "রড", "Steel rod", "KG", "CONSUMABLE"),
            new Mat("SAND", "বালু", "Sand", "CFT", "CONSUMABLE"),
            new Mat("BRICK", "ইট", "Brick", "NOS", "CONSUMABLE"),
            new Mat("STONE_CHIPS", "পাথর / খোয়া", "Stone chips / khoa", "CFT", "CONSUMABLE"),
            new Mat("WOOD", "কাঠ", "Wood / timber", "CFT", "CONSUMABLE"),
            new Mat("BAMBOO", "বাঁশ", "Bamboo", "NOS", "RENTABLE"),
            new Mat("STEEL_SHUTTER", "স্টিল সাটার", "Steel shutter plate", "NOS", "RENTABLE"),
            new Mat("PROP", "জ্যাক / প্রপ", "Steel prop / jack", "NOS", "RENTABLE"),
            new Mat("PIN_CLAMP", "পিন ও ক্ল্যাম্প", "Pins & clamps", "NOS", "RENTABLE"),
            new Mat("MIXER", "মিক্সার মেশিন", "Concrete mixer", "NOS", "RENTABLE"),
            new Mat("VIBRATOR", "ভাইব্রেটর", "Vibrator", "NOS", "RENTABLE"),
            new Mat("CABLE", "বৈদ্যুতিক তার", "Electric cable", "RFT", "CONSUMABLE"),
            new Mat("BINDING_WIRE", "বাইন্ডিং তার", "Binding wire", "KG", "CONSUMABLE"),
            new Mat("NAIL", "পেরেক", "Nails", "KG", "CONSUMABLE"));

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
        for (int i = 0; i < MATERIALS.size(); i++) {
            Mat m = MATERIALS.get(i);
            jdbc.update("""
                    INSERT INTO material (contractor_id, code, name_bn, name_en, uom, kind, sort_order, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)""",
                    contractorId, m.code(), m.nameBn(), m.nameEn(), m.uom(), m.kind(), (i + 1) * 10, now, now);
        }
    }
}
