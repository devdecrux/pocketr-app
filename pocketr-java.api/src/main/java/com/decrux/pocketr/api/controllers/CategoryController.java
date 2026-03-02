package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.CategoryDto;
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto;
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto;
import com.decrux.pocketr.api.services.category.ManageCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

    private final ManageCategory manageCategory;

    public CategoryController(ManageCategory manageCategory) {
        this.manageCategory = manageCategory;
    }

    @GetMapping
    public List<CategoryDto> listCategories(@AuthenticationPrincipal User user) {
        return manageCategory.listCategories(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(
            @RequestBody CreateCategoryDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageCategory.createCategory(dto, user);
    }

    @PatchMapping("/{id}")
    public CategoryDto updateCategory(
            @PathVariable UUID id,
            @RequestBody UpdateCategoryDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageCategory.updateCategory(id, dto, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        manageCategory.deleteCategory(id, user);
    }
}
