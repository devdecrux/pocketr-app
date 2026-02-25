package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.CategoryDto
import com.decrux.pocketr_api.entities.dtos.CreateCategoryDto
import com.decrux.pocketr_api.entities.dtos.UpdateCategoryDto
import com.decrux.pocketr_api.services.category.ManageCategory
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/categories")
class CategoryController(
    private val manageCategory: ManageCategory,
) {

    @GetMapping
    fun listCategories(@AuthenticationPrincipal user: User): List<CategoryDto> {
        return manageCategory.listCategories(user)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @RequestBody dto: CreateCategoryDto,
        @AuthenticationPrincipal user: User,
    ): CategoryDto {
        return manageCategory.createCategory(dto, user)
    }

    @PatchMapping("/{id}")
    fun updateCategory(
        @PathVariable id: UUID,
        @RequestBody dto: UpdateCategoryDto,
        @AuthenticationPrincipal user: User,
    ): CategoryDto {
        return manageCategory.updateCategory(id, dto, user)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageCategory.deleteCategory(id, user)
    }
}
