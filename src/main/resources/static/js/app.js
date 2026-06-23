function updateSliderValue(slider, outputId) {
    const output = document.getElementById(outputId);
    if (output) {
        output.textContent = slider.value;
    }
}

function calculateMealCalories() {
    const protein = Number(document.getElementById("proteins")?.value || 0);
    const fat = Number(document.getElementById("fats")?.value || 0);
    const carbs = Number(document.getElementById("carbohydrates")?.value || 0);
    const calories = Math.round((protein * 4 + fat * 9 + carbs * 4) * 100) / 100;
    const calorieInput = document.getElementById("calories");

    if (calorieInput && calories > 0) {
        calorieInput.value = calories;
        calorieInput.dispatchEvent(new Event("input", { bubbles: true }));
    }
}

function filterNutritionRows() {
    const search = document.getElementById("nutritionSearch");
    const mealFilter = document.getElementById("mealFilter");
    const rows = document.querySelectorAll("[data-nutrition-row]");
    const term = (search?.value || "").trim().toLowerCase();
    const meal = mealFilter?.value || "ALL";
    let visibleCount = 0;
    const totals = {
        calories: 0,
        protein: 0,
        carbs: 0,
        fat: 0
    };

    rows.forEach((row) => {
        const text = getNutritionRowText(row);
        const rowMeal = row.querySelector("select")?.value || row.getAttribute("data-meal-type") || "";
        const visible = (!term || text.includes(term)) && (meal === "ALL" || rowMeal === meal);
        row.classList.toggle("is-hidden", !visible);

        if (visible) {
            visibleCount += 1;
            totals.calories += getNutrientValue(row, "calories");
            totals.protein += getNutrientValue(row, "protein");
            totals.carbs += getNutrientValue(row, "carbs");
            totals.fat += getNutrientValue(row, "fat");
        }
    });

    setText("visibleRows", visibleCount);
    setText("visibleCalories", formatNutritionNumber(totals.calories));
    setText("visibleProtein", formatNutritionNumber(totals.protein));
    setText("visibleCarbs", formatNutritionNumber(totals.carbs));
    setText("visibleFat", formatNutritionNumber(totals.fat));
}

function getNutritionRowText(row) {
    const values = Array.from(row.querySelectorAll("input, select"))
        .map((field) => {
            if (field.tagName === "SELECT") {
                return `${field.value} ${field.selectedOptions[0]?.textContent || ""}`;
            }
            return field.value;
        })
        .join(" ");

    return `${row.textContent} ${values}`.toLowerCase();
}

function getNutrientValue(row, nutrient) {
    return Number(row.querySelector(`[data-nutrient="${nutrient}"]`)?.value || 0);
}

function formatNutritionNumber(value) {
    return Math.round(value * 10) / 10;
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-range-output]").forEach((slider) => {
        updateSliderValue(slider, slider.dataset.rangeOutput);
        slider.addEventListener("input", () => updateSliderValue(slider, slider.dataset.rangeOutput));
    });

    document.getElementById("calculateCalories")?.addEventListener("click", calculateMealCalories);
    document.getElementById("nutritionSearch")?.addEventListener("input", filterNutritionRows);
    document.getElementById("mealFilter")?.addEventListener("change", filterNutritionRows);
    document.querySelectorAll("[data-nutrition-row] input, [data-nutrition-row] select").forEach((field) => {
        field.addEventListener("input", filterNutritionRows);
        field.addEventListener("change", filterNutritionRows);
    });
    filterNutritionRows();
});
