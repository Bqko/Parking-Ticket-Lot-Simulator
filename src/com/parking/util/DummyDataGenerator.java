package com.parking.util;

import com.parking.enums.VehicleType;
import com.parking.model.Vehicle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic-looking dummy data for testing the Parking Lot Ticket Simulator.
 *
 * <p>All generated data follows Georgian conventions:
 * <ul>
 *   <li>License plates — Georgia's standard format (e.g. {@code ABC-123})</li>
 *   <li>Names         — Common Georgian first and last names</li>
 *   <li>Phone numbers — Georgian mobile format ({@code +995 5XX XXX XXX})</li>
 * </ul>
 *
 * <h3>Usage in tests</h3>
 * <pre>
 *   // Single items
 *   String plate   = DummyDataGenerator.plate();           // "GEL-447"
 *   String name    = DummyDataGenerator.fullName();        // "გიორგი ბერიძე"
 *   String phone   = DummyDataGenerator.phone();           // "+995 555 123 456"
 *   Vehicle v      = DummyDataGenerator.vehicle();         // random type
 *   Vehicle car    = DummyDataGenerator.vehicle(VehicleType.CAR);
 *
 *   // Bulk
 *   List&lt;Vehicle&gt; fleet = DummyDataGenerator.vehicles(10);
 *   List&lt;String&gt;  plates = DummyDataGenerator.plates(5);
 * </pre>
 *
 * <p>Pass a fixed seed to {@link #withSeed(long)} for reproducible test runs.</p>
 */
public class DummyDataGenerator {

    // ── Singleton / seed ──────────────────────────────────────────────────

    private static Random rng = new Random();

    /** Returns a new generator instance backed by a fixed seed — useful for reproducible tests. */
    public static DummyDataGenerator withSeed(long seed) {
        DummyDataGenerator g = new DummyDataGenerator();
        g.localRng = new Random(seed);
        return g;
    }

    /** Per-instance RNG (used when calling instance methods via {@link #withSeed}). */
    private Random localRng = rng;

    // ── Georgian license plate data ───────────────────────────────────────

    /**
     * Georgia uses a 3-letter + 3-digit format: {@code ABC-123}.
     * Letters are from a subset that avoids visually ambiguous chars.
     */
    private static final String PLATE_LETTERS = "ABCDEFGHJKLMNPRSTUVWXZ";

    // ── Georgian names ────────────────────────────────────────────────────

    private static final String[] FIRST_NAMES_MALE = {
            "გიორგი", "დავით", "ლაშა", "ნიკა", "სანდრო",
            "ბექა", "ლევანი", "გიგა", "ზურა", "ვახო",
            "თორნიკე", "ილია", "ნათან", "ანდრია", "შოთა"
    };

    private static final String[] FIRST_NAMES_FEMALE = {
            "ნინო", "ანა", "მარიამ", "სალომე", "თამარ",
            "ეკა", "ქეთი", "ლია", "სოფო", "ნატო",
            "მაია", "ელენე", "ირინა", "ხათუნა", "ნანა"
    };

    private static final String[] LAST_NAMES = {
            "ბერიძე", "კვარაცხელია", "ჩიქოვანი", "გელაშვილი", "მამულაშვილი",
            "ხაჩაპურიძე", "ლომიძე", "ნაკაშიძე", "სულაქველიძე", "ვარდოსანიძე",
            "ჯანელიძე", "მელიქიშვილი", "ახვლედიანი", "თოფჩიშვილი", "გოგიჩაიშვილი",
            "ჭაბუკიანი", "ბარბაქაძე", "ყიფიანი", "ელიზბარაშვილი", "სხირტლაძე"
    };

    // Latin transliterations (for contexts where Georgian script may not render)
    private static final String[] FIRST_NAMES_MALE_LATIN = {
            "Giorgi", "Davit", "Lasha", "Nika", "Sandro",
            "Beka", "Levani", "Giga", "Zura", "Vakho",
            "Tornike", "Ilia", "Natan", "Andria", "Shota"
    };

    private static final String[] FIRST_NAMES_FEMALE_LATIN = {
            "Nino", "Ana", "Mariam", "Salome", "Tamar",
            "Eka", "Qeti", "Lia", "Sopho", "Nato",
            "Maia", "Elene", "Irina", "Khatuna", "Nana"
    };

    private static final String[] LAST_NAMES_LATIN = {
            "Beridze", "Kvaratskhelia", "Chikovani", "Gelashvili", "Mamulashvili",
            "Khachapuridze", "Lomidze", "Nakashidze", "Sulaqvelidze", "Vardosanidze",
            "Janelidze", "Melikishvili", "Akhvlediani", "Topchishvili", "Gogichaishvili",
            "Chabukiani", "Barbaqadze", "Kipiani", "Elizbarashvili", "Skhirtladze"
    };

    // ── Georgian mobile prefixes ──────────────────────────────────────────
    // Georgian mobile operators: Magti (555, 599), Geocell/Silknet (514, 577),
    // MagtiCom (591, 593), Beeline (568, 579)
    private static final String[] MOBILE_PREFIXES = {
            "555", "599", "514", "577", "591", "593", "568", "579", "598", "551"
    };

    // ── Static factory methods (use shared RNG) ───────────────────────────

    /**
     * Generates a random Georgian license plate in {@code ABC-123} format.
     * Example: {@code "TBL-472"}
     */
    public static String plate() {
        return generatePlate(rng);
    }

    /**
     * Generates {@code count} unique license plates.
     * Uniqueness is guaranteed within the returned list.
     */
    public static List<String> plates(int count) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            String p = plate();
            if (!result.contains(p)) result.add(p);
        }
        return result;
    }

    /**
     * Generates a random Georgian first name (Georgian script).
     * Randomly picks male or female.
     */
    public static String firstName() {
        return rng.nextBoolean()
                ? pick(rng, FIRST_NAMES_MALE)
                : pick(rng, FIRST_NAMES_FEMALE);
    }

    /**
     * Generates a random Georgian first name in Latin transliteration.
     */
    public static String firstNameLatin() {
        return rng.nextBoolean()
                ? pick(rng, FIRST_NAMES_MALE_LATIN)
                : pick(rng, FIRST_NAMES_FEMALE_LATIN);
    }

    /** Generates a random Georgian last name (Georgian script). */
    public static String lastName() {
        return pick(rng, LAST_NAMES);
    }

    /** Generates a random Georgian last name in Latin transliteration. */
    public static String lastNameLatin() {
        return pick(rng, LAST_NAMES_LATIN);
    }

    /**
     * Generates a full name in Georgian script.
     * Example: {@code "გიორგი ბერიძე"}
     */
    public static String fullName() {
        return firstName() + " " + lastName();
    }

    /**
     * Generates a full name in Latin transliteration.
     * Example: {@code "Giorgi Beridze"}
     */
    public static String fullNameLatin() {
        return firstNameLatin() + " " + lastNameLatin();
    }

    /**
     * Generates a Georgian mobile phone number.
     * Format: {@code +995 5XX XXX XXX}
     * Example: {@code "+995 555 234 781"}
     */
    public static String phone() {
        return generatePhone(rng);
    }

    /**
     * Generates {@code count} phone numbers (may contain duplicates for large counts
     * since the format space is large enough for typical test sizes).
     */
    public static List<String> phones(int count) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(phone());
        return result;
    }

    /**
     * Generates a {@link Vehicle} with a random plate and random {@link VehicleType}.
     */
    public static Vehicle vehicle() {
        VehicleType type = randomVehicleType(rng);
        return new Vehicle(plate(), type);
    }

    /**
     * Generates a {@link Vehicle} with a random plate and the given {@link VehicleType}.
     */
    public static Vehicle vehicle(VehicleType type) {
        return new Vehicle(plate(), type);
    }

    /**
     * Generates a {@link Vehicle} with a random plate, given type, and explicit entry time.
     * Useful for testing fee calculations at specific durations.
     */
    public static Vehicle vehicle(VehicleType type, LocalDateTime entryTime) {
        return new Vehicle(plate(), type, entryTime);
    }

    /**
     * Generates {@code count} vehicles with unique plates and random types.
     */
    public static List<Vehicle> vehicles(int count) {
        List<String> uniquePlates = plates(count);
        List<Vehicle> result = new ArrayList<>(count);
        for (String p : uniquePlates) {
            result.add(new Vehicle(p, randomVehicleType(rng)));
        }
        return result;
    }

    /**
     * Generates {@code count} vehicles all of the specified type, with unique plates.
     */
    public static List<Vehicle> vehicles(int count, VehicleType type) {
        List<String> uniquePlates = plates(count);
        List<Vehicle> result = new ArrayList<>(count);
        for (String p : uniquePlates) {
            result.add(new Vehicle(p, type));
        }
        return result;
    }

    // ── Instance methods (use seeded RNG for reproducibility) ─────────────

    /** Seeded version of {@link #plate()}. */
    public String nextPlate()         { return generatePlate(localRng); }

    /** Seeded version of {@link #fullName()}. */
    public String nextFullName()      {
        boolean male = localRng.nextBoolean();
        String first = male ? pick(localRng, FIRST_NAMES_MALE) : pick(localRng, FIRST_NAMES_FEMALE);
        return first + " " + pick(localRng, LAST_NAMES);
    }

    /** Seeded version of {@link #fullNameLatin()}. */
    public String nextFullNameLatin() {
        boolean male = localRng.nextBoolean();
        String first = male ? pick(localRng, FIRST_NAMES_MALE_LATIN) : pick(localRng, FIRST_NAMES_FEMALE_LATIN);
        return first + " " + pick(localRng, LAST_NAMES_LATIN);
    }

    /** Seeded version of {@link #phone()}. */
    public String nextPhone()         { return generatePhone(localRng); }

    /** Seeded version of {@link #vehicle()}. */
    public Vehicle nextVehicle()      { return new Vehicle(nextPlate(), randomVehicleType(localRng)); }

    /** Seeded version of {@link #vehicle(VehicleType)}. */
    public Vehicle nextVehicle(VehicleType type) { return new Vehicle(nextPlate(), type); }

    // ── Private helpers ───────────────────────────────────────────────────

    private static String generatePlate(Random r) {
        char l1 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l2 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        int  n  = 100 + r.nextInt(900); // 100–999
        char l3 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l4 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        return "" + l1 + l2 + "-" + n + "-" + l3 + l4;
    }

    private static String generatePhone(Random r) {
        String prefix = pick(r, MOBILE_PREFIXES);
        int part1 = 100 + r.nextInt(900); // 100–999
        int part2 = 100 + r.nextInt(900); // 100–999
        return "+995 " + prefix + " " + part1 + " " + part2;
    }

    private static <T> T pick(Random r, T[] array) {
        return array[r.nextInt(array.length)];
    }

    private static VehicleType randomVehicleType(Random r) {
        VehicleType[] types = VehicleType.values();
        return types[r.nextInt(types.length)];
    }
}