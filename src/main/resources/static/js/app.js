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

    rows.forEach((row) => {
        const text = row.textContent.toLowerCase();
        const rowMeal = row.getAttribute("data-meal-type") || "";
        const visible = (!term || text.includes(term)) && (meal === "ALL" || rowMeal === meal);
        row.classList.toggle("is-hidden", !visible);
        if (visible) {
            visibleCount += 1;
        }
    });

    const counter = document.getElementById("visibleRows");
    if (counter) {
        counter.textContent = visibleCount;
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
    filterNutritionRows();
});
