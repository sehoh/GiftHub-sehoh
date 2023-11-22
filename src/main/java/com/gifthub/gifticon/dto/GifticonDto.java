package com.gifthub.gifticon.dto;

import com.gifthub.gifticon.entity.Gifticon;
import com.gifthub.payment.enumeration.SaleStatus;
import com.gifthub.user.dto.UserDto;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@NoArgsConstructor
@Builder
@Getter
public class GifticonDto {

    private Long id;
    private UserDto user;
    private String barcode;
    private LocalDate due;
    private String brandName;
    private String productName;
    private SaleStatus saleStatus;
    private Long price;

    public Gifticon toEntity() {
        return Gifticon.builder()
                .id(this.id)
                .user(this.user.toEntity())
                .barcode(this.barcode)
                .due(this.due)
                .brandName(this.brandName)
                .productName(this.productName)
                .saleStatus(this.saleStatus)
                .price(this.price)
                .build();
    }
    @QueryProjection
    public GifticonDto(Long id, UserDto user, String barcode, LocalDate due, String brandName, String productName, SaleStatus saleStatus, Long price) {
        this.id = id;
        this.user = user;
        this.barcode = barcode;
        this.due = due;
        this.brandName = brandName;
        this.productName = productName;
        this.saleStatus = saleStatus;
        this.price = price;
    }
}
