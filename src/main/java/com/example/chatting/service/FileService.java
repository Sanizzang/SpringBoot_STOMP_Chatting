package com.example.chatting.service;

import com.example.chatting.dto.FileUploadDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public interface FileService {
    // 파일 업로드를 위한 메서드 선언
    FileUploadDto uploadFile(MultipartFile file, String transaction, String roomId);

    // 현재 방에 업로드된 모든 파일 삭제 메서드
    void deleteFileDir(String path);

    // 컨트롤러에서 받아온 multipartFile 을 File 로 변환시켜서 저장하기 위한 메서드
    // MultipartFile: Spring에서 제공하는 인터페이스로, 업로드된 파일을 다루기 위한 기능 제공
    default File convertMultipartFileToFile(MultipartFile mfile, String tmpPath) throws IOException {
        File file = new File(tmpPath);

        // file.createNewFile()를 이용하여 새로운 파일 객체 생성
        if (file.createNewFile()) {
            // MultipartFile 객체의 내용을 파일에 기록
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(mfile.getBytes());
            }
            return file;
        }
        throw new IOException();
    }

    // 파일 삭제
    default void removeFile(File file){
        file.delete();
    }

    ResponseEntity<byte[]> getObject(String fileDir, String fileName) throws IOException;
}
