package com.parking.util;

import com.parking.enums.VehicleType;
import com.parking.model.Vehicle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Generates realistic-looking dummy data for testing the Parking Lot Ticket Simulator.
 *
 * <p>All generated data follows Georgian conventions:
 * <ul>
 *   <li>License plates (car/truck)  — {@code AB-123-CD} format</li>
 *   <li>License plates (motorcycle) — {@code 12/ABCD} format (2 digits / 4 letters)</li>
 *   <li>Names         — Common Georgian first and last names</li>
 *   <li>Phone numbers — Georgian mobile format ({@code +995 5XX XXX XXX})</li>
 * </ul>
 *
 * <h3>Usage in tests</h3>
 * <pre>
 *   // Single items
 *   String plate      = DummyDataGenerator.plate();                        // "TBL-472-KN" (car)
 *   String motoPlate  = DummyDataGenerator.plate(VehicleType.MOTORCYCLE);  // "47/BKRS"
 *   String name       = DummyDataGenerator.fullName();                     // "გიორგი ბერიძე"
 *   String phone      = DummyDataGenerator.phone();                        // "+995 555 123 456"
 *   Vehicle v         = DummyDataGenerator.vehicle();                      // random type
 *   Vehicle car       = DummyDataGenerator.vehicle(VehicleType.CAR);
 *
 *   // Bulk
 *   List&lt;Vehicle&gt; fleet = DummyDataGenerator.vehicles(10);
 *   List&lt;String&gt;  plates = DummyDataGenerator.plates(5);
 * </pre>
 *
 * <p>Pass a fixed seed to {@link #withSeed(long)} for reproducible test runs.
 * An instance created via {@link #withSeed(long)} also guarantees every plate
 * it hands out through {@link #nextPlate()} / {@link #nextPlate(VehicleType)}
 * is unique for the lifetime of that instance — handy when seeding dozens of
 * dummy tickets that must never collide on license plate.</p>
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

    /**
     * Plates already handed out by THIS instance's {@link #nextPlate()} /
     * {@link #nextPlate(VehicleType)}. Ensures no two tickets seeded from the
     * same generator instance ever share a license plate.
     */
    private final Set<String> usedPlates = new HashSet<>();

    // ── Georgian license plate data ───────────────────────────────────────

    /**
     * Car/truck plates: 2 letters + 3 digits + 2 letters → {@code AB-123-CD}.
     * Motorcycle plates: 2 digits + slash + 4 letters   → {@code 12/ABCD}.
     * Both use this letter pool to avoid visually ambiguous characters.
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
     * Generates a random Georgian license plate for the given vehicle type.
     * <ul>
     *   <li>CAR / TRUCK  → {@code AB-123-CD}</li>
     *   <li>MOTORCYCLE   → {@code 12/ABCD}</li>
     * </ul>
     */
    public static String plate(VehicleType type) {
        return type == VehicleType.MOTORCYCLE
                ? generateMotorcyclePlate(rng)
                : generatePlate(rng);
    }

    /**
     * Generates a random Georgian license plate in {@code AB-123-CD} format.
     * For motorcycle plates use {@link #plate(VehicleType)}.
     * Example: {@code "TK-472-NB"}
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
     * Generates a {@link Vehicle} with a random plate (format matches the type)
     * and random {@link VehicleType}.
     */
    public static Vehicle vehicle() {
        VehicleType type = randomVehicleType(rng);
        return new Vehicle(plate(type), type);
    }

    /**
     * Generates a {@link Vehicle} with a type-appropriate plate and the given {@link VehicleType}.
     */
    public static Vehicle vehicle(VehicleType type) {
        return new Vehicle(plate(type), type);
    }

    /**
     * Generates a {@link Vehicle} with a type-appropriate plate, given type, and explicit entry time.
     * Useful for testing fee calculations at specific durations.
     */
    public static Vehicle vehicle(VehicleType type, LocalDateTime entryTime) {
        return new Vehicle(plate(type), type, entryTime);
    }

    /**
     * Generates {@code count} vehicles with unique plates and random types.
     * Each vehicle's plate format matches its type.
     */
    public static List<Vehicle> vehicles(int count) {
        List<Vehicle> result = new ArrayList<>(count);
        // Track used plates per format to guarantee uniqueness across types
        List<String> usedPlates = new ArrayList<>();
        while (result.size() < count) {
            VehicleType type = randomVehicleType(rng);
            String p = type == VehicleType.MOTORCYCLE
                    ? generateMotorcyclePlate(rng)
                    : generatePlate(rng);
            if (!usedPlates.contains(p)) {
                usedPlates.add(p);
                result.add(new Vehicle(p, type));
            }
        }
        return result;
    }

    /**
     * Generates {@code count} vehicles all of the specified type, with unique plates.
     * Motorcycle vehicles get {@code 12/ABCD} format; others get {@code AB-123-CD}.
     */
    public static List<Vehicle> vehicles(int count, VehicleType type) {
        List<String> uniquePlates = type == VehicleType.MOTORCYCLE
                ? motorcyclePlates(count)
                : plates(count);
        List<Vehicle> result = new ArrayList<>(count);
        for (String p : uniquePlates) {
            result.add(new Vehicle(p, type));
        }
        return result;
    }

    // ── Instance methods (use seeded RNG for reproducibility) ─────────────

    /**
     * Seeded version of {@link #plate()} — generates a car/truck format plate.
     * Guaranteed not to repeat a plate already handed out by this instance.
     */
    public String nextPlate() {
        return uniquePlate(() -> generatePlate(localRng));
    }

    /**
     * Seeded version of {@link #plate(VehicleType)} — format matches the given type.
     * Guaranteed not to repeat a plate already handed out by this instance.
     */
    public String nextPlate(VehicleType type) {
        return uniquePlate(() -> type == VehicleType.MOTORCYCLE
                ? generateMotorcyclePlate(localRng)
                : generatePlate(localRng));
    }

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
    public Vehicle nextVehicle() {
        VehicleType type = randomVehicleType(localRng);
        return new Vehicle(nextPlate(type), type);
    }

    /** Seeded version of {@link #vehicle(VehicleType)}. */
    public Vehicle nextVehicle(VehicleType type) { return new Vehicle(nextPlate(type), type); }

    /**
     * Clears this instance's record of previously issued plates, so
     * {@link #nextPlate()} / {@link #nextPlate(VehicleType)} may reuse
     * earlier values again. Not needed in normal use — only call this
     * if you intentionally want to start a fresh uniqueness window on
     * the same generator instance.
     */
    public void resetUsedPlates() {
        usedPlates.clear();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Repeatedly invokes {@code generator} until it produces a plate not yet
     * seen by this instance, records it, and returns it. Bails out with an
     * {@link IllegalStateException} rather than looping forever if the
     * format's plate space is somehow exhausted.
     */
    private String uniquePlate(Supplier<String> generator) {
        String candidate;
        int attempts = 0;
        do {
            candidate = generator.get();
            if (++attempts > 10_000) {
                throw new IllegalStateException(
                        "Could not generate a unique plate after 10,000 attempts — plate pool exhausted.");
            }
        } while (!usedPlates.add(candidate));
        return candidate;
    }

    /** Car/truck format: {@code AB-123-CD} */
    private static String generatePlate(Random r) {
        char l1 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l2 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        int  n  = 100 + r.nextInt(900); // 100–999
        char l3 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l4 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        return "" + l1 + l2 + "-" + n + "-" + l3 + l4;
    }

    /** Motorcycle format: {@code 12/ABCD} — 2 digits, slash, 4 letters */
    private static String generateMotorcyclePlate(Random r) {
        int  n  = 10 + r.nextInt(90);   // 10–99
        char l1 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l2 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l3 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        char l4 = PLATE_LETTERS.charAt(r.nextInt(PLATE_LETTERS.length()));
        return "" + n + "/" + l1 + l2 + l3 + l4;
    }

    /**
     * Generates {@code count} unique motorcycle license plates.
     */
    public static List<String> motorcyclePlates(int count) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            String p = generateMotorcyclePlate(rng);
            if (!result.contains(p)) result.add(p);
        }
        return result;
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