package Evan.Application.Fitness.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor

public class Nutrition {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        //private double calories;
        //@JsonProperty("serving_size_g")
        //private double servingSize;
        @JsonProperty("fat_total_g")
        private double totalFat;
        @JsonProperty("fat_saturated_g")
        private double saturatedFat;
        //@JsonProperty("protein_g")
        //private double protein;
        @JsonProperty("sodium_mg")
        private double sodium;
        @JsonProperty("potassium_mg")
        private double potassium;
        @JsonProperty("cholesterol_mg")
        private double cholesterol;
        @JsonProperty("carbohydrates_total_g")
        private double totalCarbohydrates;
        @JsonProperty("fiber_g")
        private double fiber;
        @JsonProperty("sugar_g")
        private double sugar;
        @OneToOne
        @JoinColumn(name = "userid")
        private UserLoginDetails userLoginDetails;

    }





