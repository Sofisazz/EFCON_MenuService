package com.example.menuservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class ShoppingListDto {
    private int recipeId;
    private String recipeName;
    private List<ProductStatusDto> needToBuy;
}
