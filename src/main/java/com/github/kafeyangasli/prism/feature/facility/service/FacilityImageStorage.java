package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.shared.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FacilityImageStorage extends LocalImageStorage {
    public FacilityImageStorage(@Value("${prism.storage.facilities:./storage/facilities}") String directory,
                                @Value("${prism.facility-images.max-bytes:5242880}") int maxBytes) {
        super(directory, maxBytes);
    }
}
