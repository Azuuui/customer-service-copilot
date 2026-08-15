package com.example.copilot.operations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.concurrent.TimeUnit;

@RestController
public class AnnouncementController {
    private final AnnouncementService service; private final OperationsActor actors;
    public AnnouncementController(AnnouncementService service,OperationsActor actors){this.service=service;this.actors=actors;}
    @GetMapping("/api/v1/announcements") public AnnouncementService.Page visible(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return service.list(true,page,Math.min(Math.max(size,1),100));}
    @GetMapping("/api/v1/announcement-images/{id}") public ResponseEntity<byte[]> image(@PathVariable long id){var image=service.image(id);return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1,TimeUnit.DAYS)).contentType(MediaType.parseMediaType(image.mimeType())).body(image.content());}
    @GetMapping("/api/v1/admin/announcements") public AnnouncementService.Page all(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return service.list(false,page,Math.min(Math.max(size,1),100));}
    @PostMapping("/api/v1/admin/announcements") public AnnouncementService.Item save(@Valid @RequestBody SaveRequest body,HttpServletRequest request){String employee=actors.employee(request);return service.save(employee,actors.userId(employee),new AnnouncementService.Save(body.title(),body.content(),body.contentFormat(),body.pinned(),body.publishAt(),body.expireAt(),body.images()));}
    @PostMapping("/api/v1/admin/announcements/{id}/withdraw") public AnnouncementService.Item withdraw(@PathVariable long id,HttpServletRequest request){String employee=actors.employee(request);return service.withdraw(employee,actors.userId(employee),id);}
    public record SaveRequest(@NotBlank String title,@NotBlank String content,String contentFormat,boolean pinned,Instant publishAt,Instant expireAt,List<AnnouncementService.Image> images){}
}
