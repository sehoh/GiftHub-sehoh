package com.gifthub.gifticon.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.gifthub.gifticon.dto.GifticonImageDto;
import com.gifthub.gifticon.entity.GifticonImage;
import com.gifthub.gifticon.entity.GifticonStorage;
import com.gifthub.gifticon.repository.GifticonImageRepository;
import com.gifthub.gifticon.util.GifticonImageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class GifticonImageService {
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    private final AmazonS3Client amazonS3Client;

    private final GifticonImageRepository gifticonImageRepository;

    // TODO : 파일 여러개 넣는 기능
//    @Transactional
//    public List<String> saveImages(ImageSaveDto saveDto) {
//        List<String> resultList = new ArrayList<>();
//
//        for(MultipartFile multipartFile : saveDto.getGifticonImages()) {
//            String value = saveImage(multipartFile);
//            resultList.add(value);
//        }
//
//        return resultList;
//    }

    @Transactional
    public GifticonImageDto saveImage(MultipartFile multipartFile) {
        String originalName = multipartFile.getOriginalFilename();
        GifticonImage image = new GifticonImage(originalName);
        String filename = image.getStoreFileName();

        try {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(multipartFile.getContentType());
            objectMetadata.setContentLength(multipartFile.getInputStream().available());

            amazonS3Client.putObject(bucketName, filename, multipartFile.getInputStream(), objectMetadata);

            String accessUrl = amazonS3Client.getUrl(bucketName, filename).toString();
            image.setAccessUrl(accessUrl);
        } catch(IOException e) {
            System.out.println("서버에 이미지 저장 실패");
        }

        GifticonImageDto gifticonImageDto = gifticonImageRepository.save(image).toGifticonImageDto();

        return gifticonImageDto;
    }

    // TODO : url로 서버에 바로 저장하는 메서드
    @Transactional
    public GifticonImageDto saveImageByUrl(String imageUrl){
        String originalName = GifticonImageUtil.parseImgUrlToFilename(imageUrl);
        GifticonImage image = new GifticonImage(originalName);
        String filename = image.getStoreFileName();
        try{
            InputStream inputStream = new URL(imageUrl).openStream();
            amazonS3Client.putObject(new PutObjectRequest(bucketName, filename,inputStream, null));

            String accessUrl = amazonS3Client.getUrl(bucketName, filename).toString();
            image.setAccessUrl(accessUrl);

        } catch (IOException e){
            System.out.println("서버에 이미지 저장 실패");
        }

        GifticonImageDto gifticonImageDto = gifticonImageRepository.save(image).toGifticonImageDto();

        return gifticonImageDto;
    }


    // 삭제
    public void deleteFileByStorage(GifticonStorage storage){
        GifticonImage gifticonImage = gifticonImageRepository.findGifticonImageByGifticonStorage(storage).get();
        DeleteObjectRequest request = new DeleteObjectRequest(bucketName, gifticonImage.getStoreFileName());
        amazonS3Client.deleteObject(request); // 서버에서 삭제
        gifticonImageRepository.deleteById(gifticonImage.getId()); // db에서 삭제
    }




}
