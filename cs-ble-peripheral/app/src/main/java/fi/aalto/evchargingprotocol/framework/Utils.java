package fi.aalto.evchargingprotocol.framework;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Utils {

    private static DateTimeFormatter standardFormatter = DateTimeFormatter.ISO_INSTANT;

    private Utils() {}

    static String getCurrentDateInStandardFormat() {
        return LocalDate.now().format(Utils.standardFormatter);
    }
}