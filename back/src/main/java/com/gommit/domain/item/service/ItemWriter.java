package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemImage;
import com.gommit.domain.item.entity.Pose;
import com.gommit.domain.item.repository.ItemImageRepository;
import com.gommit.domain.item.repository.ItemRepository;
import com.gommit.domain.media.service.StorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ItemWriter {
    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final StorageService storageService;

    @Transactional
    public ItemResponse saveItemWithImages(ItemCreateRequest request, List<String> uploadedKeys) {
        Item item = Item.of(request.slot(), request.name(), request.price());
        itemRepository.save(item);

        String defaultImageUrl = null;
        for (int i = 0; i < request.poses().size(); i++) {
            Pose pose = request.poses().get(i);
            String key = uploadedKeys.get(i);
            itemImageRepository.save(ItemImage.of(item, pose, key));
            if (pose == Pose.DEFAULT) {
                defaultImageUrl = storageService.publicUrl(key);
            }
        }
        return new ItemResponse(item, defaultImageUrl);
    }
}
