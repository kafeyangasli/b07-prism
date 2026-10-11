package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.shared.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Private files are served only after report authorization. */
@Service
public class ReportPhotoStorage extends LocalImageStorage {
    public ReportPhotoStorage(@Value("${prism.storage.reports:./uploads}") String directory) {
        super(directory, 10 * 1024 * 1024);
    }
}
