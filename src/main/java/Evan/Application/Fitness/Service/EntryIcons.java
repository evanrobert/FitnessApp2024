package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.WorkoutSession;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Picks a friendly icon for an entry: a shake for a protein shake, a bicep for a lifting workout. */
public final class EntryIcons {
    private record Rule(Pattern pattern, String icon) {
    }

    // First match wins, so drinks come before meats ("chicken soup" is a bowl, "protein shake" a shake).
    private static final List<Rule> FOOD = List.of(
            new Rule(words("shake", "smoothie", "protein drink", "whey", "milk"), "shake"),
            new Rule(words("coffee", "latte", "espresso", "cappuccino", "tea", "americano"), "coffee"),
            new Rule(words("soup", "chili", "stew", "oat", "oats", "oatmeal", "cereal", "rice", "pasta", "noodle", "noodles", "bowl", "ramen", "curry"), "bowl"),
            new Rule(words("salad", "greens", "spinach", "kale", "broccoli", "veggies", "vegetables", "vegetable"), "salad"),
            new Rule(words("egg", "eggs", "omelet", "omelette", "frittata"), "egg"),
            new Rule(words("chicken", "turkey", "steak", "beef", "pork", "ribeye", "burger", "meat", "wings", "drumstick", "ham", "bacon",
                    "salmon", "tuna", "fish", "shrimp", "cod", "tilapia", "lamb", "sausage"), "drumstick"),
            new Rule(words("apple", "banana", "orange", "fruit", "berries", "berry", "strawberries", "blueberries", "grapes", "grape",
                    "pear", "peach", "mango", "melon", "watermelon", "pineapple"), "apple"));

    private EntryIcons() {
    }

    private static Pattern words(String... words) {
        return Pattern.compile("\\b(" + String.join("|", words) + ")\\b");
    }

    public static String food(String name) {
        if (name == null) {
            return "bowl";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return FOOD.stream().filter(r -> r.pattern().matcher(lower).find()).map(Rule::icon).findFirst().orElse("bowl");
    }

    /** The icon of the first food in a day that has a specific one, else a bowl. */
    public static String foods(Collection<String> names) {
        return names.stream().map(EntryIcons::food).filter(i -> !i.equals("bowl")).findFirst().orElse("bowl");
    }

    public static String workout(WorkoutSession session) {
        return session.getSets().isEmpty() && !session.getCardio().isEmpty() ? "runner" : "bicep";
    }
}
