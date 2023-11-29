package com.gifthub.gifticon.controller;

import com.gifthub.chatbot.util.JsonConverter;
import com.gifthub.exception.InvalidDueDate;
import com.gifthub.gifticon.dto.*;
import com.gifthub.gifticon.dto.storage.GifticonStorageDto;
import com.gifthub.gifticon.entity.GifticonStorage;

import com.gifthub.gifticon.service.*;
import com.gifthub.gifticon.util.GifticonImageUtil;
import com.gifthub.gifticon.util.OcrUtil;
import com.gifthub.product.dto.ProductDto;
import com.gifthub.product.service.ProductService;
import com.gifthub.user.UserJwtTokenProvider;
import com.gifthub.user.dto.UserDto;
import com.gifthub.user.service.UserService;
import com.google.zxing.NotFoundException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api")
public class GifticonController {
    private final GifticonStorageService gifticonStorageService;
    private final GifticonImageService gifticonImageService;
    private final GifticonService gifticonService;
    private final OcrService ocrService;
    private final UserService userService;
    private final ProductService productService;
    private final UserJwtTokenProvider userJwtTokenProvider;


    @PostMapping("/kakao/chatbot/add")
    public ResponseEntity<Object> addGificonByKakao(@RequestBody Map<Object, Object> gifticon,
                                                    @RequestHeader HttpHeaders headers) {
        File file = null;
        try {
            List<String> barcodeUrlList = JsonConverter.kakaoChatbotConverter(gifticon);

            for (String barcodeUrl : barcodeUrlList) {
                String barcode = GifticonService.readBarcode(barcodeUrl);
                GifticonDto gifticonDto = ocrService.readOcrUrlToGifticonDto(barcodeUrl);

                // TODO : 유효기간이 지났는지 check -> 사용자 예외
                if(gifticonDto.getDue() != null){
                    OcrUtil.checkDueDate(gifticonDto.getDue());
                }
                file = GifticonImageUtil.convertKakaoUrlToFile(barcodeUrl); // url -> File

                GifticonImageDto imageDto = gifticonImageService.saveImageByFile(file); // File -> 서버에 저장
                gifticonDto.setBarcode(barcode);
//                System.out.println("barcode : "+ barcode);
//                System.out.println("AccessUrl: "+ imageDto.getAccessUrl());
//                System.out.println("storedFileName: " + imageDto.getStoreFileName());
//                System.out.println("originalFileName: " + imageDto.getOriginalFileName());

                // UserId를 어떻게 가져오지?
                gifticonDto.setUser(userService.getUserById(userJwtTokenProvider.getUserIdFromToken(headers.get("Authorization").get(0))));
                System.out.println(headers);
                System.out.println(gifticonDto.getUser().getId());

                GifticonStorage storage = gifticonStorageService.saveStorage(gifticonDto, imageDto);
                System.out.println("sotrage_id : " + storage.getId());


            }

        } catch (NotFoundException e){ // 바코드x
            return ResponseEntity.badRequest().build();

        } catch (InvalidDueDate e){ // 유효기간 체크
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().build(); // 400이 날라감 -> ajax에

        } finally {
            file.delete();
        }

        return ResponseEntity.ok().build();  // 200이 날라감 0 -> ajax에 success
    }

    @PostMapping("/gifticon/add") // MultipartType으로 받는다 (1개)
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> addGifticonByFile(@RequestPart MultipartFile imageFile,
                                                    @RequestHeader HttpHeaders headers) {
        File file = null;
        try {
            file = GifticonImageUtil.convert(imageFile);

            GifticonDto gifticonDto = ocrService.readOcrMultipartToGifticonDto(file); // 파일

            // TODO : 유효기간이 지났는지 check -> 사용자 예외
            if(gifticonDto.getDue() != null){
                OcrUtil.checkDueDate(gifticonDto.getDue());
            }
            GifticonImageDto imageDto = gifticonImageService.saveImage(imageFile); // 이미지 서버에 저장 및 db에 경로저장

            String barcode = GifticonService.readBarcode(imageDto.getAccessUrl());
            gifticonDto.setBarcode(barcode);
            gifticonDto.setUser(userService.getUserById(userJwtTokenProvider.getUserIdFromToken(headers.get("Authorization").get(0))));

            gifticonStorageService.saveStorage(gifticonDto, imageDto);

        } catch (NotFoundException e){ // 바코드x
            return ResponseEntity.badRequest().build();

        } catch (InvalidDueDate e){ // 유효기간 체크
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();

        } finally {
            file.delete();
        }
        return ResponseEntity.ok().body(Collections.singletonMap("status", "ok"));
    }

    @PostMapping("/gifticon/addMultiple") // MultipartType으로 받는다 (여러개)
    @ResponseStatus(HttpStatus.OK)
    public List<String> addGifticonByFiles(@ModelAttribute ImageSaveDto imageSaveDto) {
//        return gifticonImageService.saveImages(imageSaveDto);
        return null;
    }


    @GetMapping("/barcode/{barcode}")
    public void barcode(@PathVariable("barcode") String barcode, HttpServletResponse response) {
        ServletOutputStream outputStream = null;

        try {
            outputStream = response.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GifticonService.writeBarcode(barcode, outputStream);
    }


    @GetMapping("/gifticons/{type}")
    public ResponseEntity<Object> gifticons(Pageable pageable, @PathVariable("type") String type) {
        return ResponseEntity.ok(gifticonService.getPurchasingGifticon(pageable, type));
    }

    @RequestMapping(value = "/gifticon/storage/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Object> getStorageList(@RequestHeader HttpHeaders headers, @PageableDefault(size = 6) Pageable pageable) {

        try {
            UserDto userDto = userService.getUserById(userJwtTokenProvider.getUserIdFromToken(headers.get("Authorization").get(0)));
            Page<GifticonStorageListDto> storageList = gifticonStorageService.getStorageList(userDto.getId(), pageable);

            return ResponseEntity.ok(storageList);

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/gifticon/register") // db에 있는경우
    public ResponseEntity<Object> registerGifticon(@RequestBody Map<String, String> request,
                                                   @RequestHeader HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();

        long storageId = Long.parseLong(request.get("id"));

        try {
            GifticonStorageDto storage = gifticonStorageService.getStorageById(storageId);
            Long userId = userJwtTokenProvider.getUserIdFromToken(headers.get("Authorization").get(0));
            UserDto findUser = userService.getUserById(userId);
            storage.setUser(findUser);

            ProductDto product = productService.getByProductName(storage.getProductName());
            GifticonDto gifticonDto = storage.toGifticonDto(product);
            gifticonService.saveGifticon(gifticonDto);
            gifticonStorageService.deleteStorage(storage.getId());

            result.put("status", "success");
            return ResponseEntity.ok().body(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

}



