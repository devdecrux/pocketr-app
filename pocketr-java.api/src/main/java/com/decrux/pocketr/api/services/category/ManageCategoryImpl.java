package com.decrux.pocketr.api.services.category;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.dtos.CategoryDto;
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto;
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto;
import com.decrux.pocketr.api.repositories.CategoryTagRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManageCategoryImpl implements ManageCategory {

    private final CategoryTagRepository categoryTagRepository;
    private final OwnershipGuard ownershipGuard;

    public ManageCategoryImpl(CategoryTagRepository categoryTagRepository, OwnershipGuard ownershipGuard) {
        this.categoryTagRepository = categoryTagRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional
    public CategoryDto createCategory(CreateCategoryDto dto, User owner) {
        long userId = requireNotNull(owner.getUserId(), "User ID must not be null");
        String name = dto.getName().trim();

        if (categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category '" + name + "' already exists");
        }

        CategoryTag tag = new CategoryTag();
        tag.setOwner(owner);
        tag.setName(name);
        tag.setColor(dto.getColor() != null && !dto.getColor().isBlank() ? dto.getColor() : null);

        try {
            return toDto(categoryTagRepository.save(tag));
        } catch (DataIntegrityViolationException ignored) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category '" + name + "' already exists");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(User owner) {
        long userId = requireNotNull(owner.getUserId(), "User ID must not be null");
        return categoryTagRepository.findByOwnerUserId(userId).stream().map(ManageCategoryImpl::toDto).toList();
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(UUID id, UpdateCategoryDto dto, User owner) {
        CategoryTag tag = categoryTagRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        ownershipGuard.requireOwner(
            tag.getOwner() != null ? tag.getOwner().getUserId() : null,
            requireNotNull(owner.getUserId(), "User ID must not be null"),
            "Not the owner of this category"
        );

        long userId = requireNotNull(owner.getUserId(), "User ID must not be null");
        String newName = dto.getName().trim();

        if (
            !newName.equalsIgnoreCase(tag.getName())
                && categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(userId, newName)
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category '" + newName + "' already exists");
        }

        tag.setName(newName);
        tag.setColor(dto.getColor() != null && !dto.getColor().isBlank() ? dto.getColor() : null);

        try {
            return toDto(categoryTagRepository.save(tag));
        } catch (DataIntegrityViolationException ignored) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category '" + newName + "' already exists");
        }
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id, User owner) {
        CategoryTag tag = categoryTagRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        ownershipGuard.requireOwner(
            tag.getOwner() != null ? tag.getOwner().getUserId() : null,
            requireNotNull(owner.getUserId(), "User ID must not be null"),
            "Not the owner of this category"
        );

        try {
            categoryTagRepository.delete(tag);
            categoryTagRepository.flush();
        } catch (DataIntegrityViolationException ignored) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category is in use and cannot be deleted");
        }
    }

    private static CategoryDto toDto(CategoryTag tag) {
        return new CategoryDto(
            requireNotNull(tag.getId(), "Category ID must not be null"),
            tag.getName(),
            tag.getColor(),
            tag.getCreatedAt()
        );
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
