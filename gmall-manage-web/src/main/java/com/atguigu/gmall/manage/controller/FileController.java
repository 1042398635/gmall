package com.atguigu.gmall.manage.controller;

import org.apache.commons.lang3.StringUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@CrossOrigin
public class FileController {

    @Value("${fileService.url}")
    String fileServiceUrl;

    @PostMapping("fileUpload")
    public String fileUpload(MultipartFile file) throws IOException, MyException {
        String confPath = this.getClass().getResource("/tracker.conf").getFile();
        ClientGlobal.init(confPath);
        TrackerClient trackerClient = new TrackerClient();
        TrackerServer trackerServer = trackerClient.getConnection();
        StorageClient storageClient = new StorageClient(trackerServer, null);
        String orginalFilename = file.getOriginalFilename();
        String extName = StringUtils.substringAfterLast(orginalFilename, ".");
        String[] upload_file = storageClient.upload_file(file.getBytes(), extName, null);
        String fileUrl = fileServiceUrl;
        for (int i = 0; i < upload_file.length; i++) {
            String s = upload_file[i];
            fileUrl += "/" + s;
        }
        return fileUrl;
    }

}
