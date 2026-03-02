package com.decrux.pocketr.api.services.category;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.CategoryDto;
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto;
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto;
import java.util.List;
import java.util.UUID;

public interface ManageCategory {

    CategoryDto createCategory(CreateCategoryDto dto, User owner);

    List<CategoryDto> listCategories(User owner);

    CategoryDto updateCategory(UUID id, UpdateCategoryDto dto, User owner);

    void deleteCategory(UUID id, User owner);
}
