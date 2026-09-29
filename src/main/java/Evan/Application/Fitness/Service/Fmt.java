package Evan.Application.Fitness.Service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Display formatting used from templates as ${@fmt.xxx(...)}. Keeps number styling consistent everywhere. */
@Component("fmt")
public class Fmt {
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US);

    /** 1,284 / 12.9K / 1.2M */
    public String compact(Number value) {
        if (value == null) {
            return "—";
        }
        double v = value.doubleValue();
        double abs = Math.abs(v);
        if (abs >= 1_000_000) {
            return trim(v / 1_000_000, 1) + "M";
        }
        if (abs >= 10_000) {
            return trim(v / 1_000, 1) + "K";
        }
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    public String num(Number value) {
        return value == null ? "—" : String.format(Locale.US, "%,d", Math.round(value.doubleValue()));
    }

    public String dec(Number value) {
        return value == null ? "—" : trim(value.doubleValue(), 1);
    }

    public String signed(Number value) {
        if (value == null) {
            return "—";
        }
        double v = value.doubleValue();
        return (v > 0 ? "+" : v < 0 ? "−" : "±") + trim(Math.abs(v), 1);
    }

    public String shortDate(LocalDate date) {
        return date == null ? "—" : date.format(SHORT);
    }

    public String fullDate(LocalDate date) {
        return date == null ? "—" : date.format(FULL);
    }

    public String dayTitle(LocalDate date) {
        return date == null ? "—" : date.format(DAY);
    }

    public String dow(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US);
    }

    public String mon(LocalDate date) {
        return date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US);
    }

    public String relative(LocalDate date, LocalDate today) {
        if (date == null) {
            return "never";
        }
        long days = ChronoUnit.DAYS.between(date, today);
        if (days == 0) return "today";
        if (days == 1) return "yesterday";
        if (days > 1 && days < 7) return days + " days ago";
        if (days >= 7 && days < 60) return (days / 7) + (days / 7 == 1 ? " week ago" : " weeks ago");
        if (days < 0) return "in " + (-days) + " days";
        return shortDate(date);
    }

    public String minutes(Number minutes) {
        if (minutes == null) {
            return "—";
        }
        long m = Math.round(minutes.doubleValue());
        return m >= 60 ? (m / 60) + "h " + String.format("%02d", m % 60) + "m" : m + " min";
    }

    /** 8.5 -> "8:30" (minutes per mile). */
    public String pace(Double minutesPerMile) {
        if (minutesPerMile == null) {
            return "—";
        }
        long seconds = Math.round(minutesPerMile * 60);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    public String pct(Number value) {
        return value == null ? "—" : Math.round(value.doubleValue()) + "%";
    }

    /** Width for meter bars, clamped 0-100. */
    public String width(Number value) {
        double v = value == null ? 0 : Math.max(0, Math.min(100, value.doubleValue()));
        return "--pct:" + trim(v, 1) + "%";
    }

    private static String trim(double v, int decimals) {
        String s = String.format(Locale.US, "%,." + decimals + "f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
