package com.example.copilot.operations;

import com.example.copilot.audit.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnouncementService {
    private final JdbcClient jdbc;
    private final AuditService audit;
    public AnnouncementService(JdbcClient jdbc, AuditService audit) { this.jdbc=jdbc; this.audit=audit; }

    public Page list(boolean visibleOnly, int page, int size) {
        String where=visibleOnly ? " WHERE publication_status IN ('published','scheduled') AND COALESCE(publish_at,created_at)<=now() AND (expire_at IS NULL OR expire_at>now())" : "";
        List<Item> items=jdbc.sql("SELECT id,title,content,content_format,publication_status,is_pinned pinned,publish_at,expire_at,published_at,withdrawn_at FROM announcements"+where+" ORDER BY is_pinned DESC,COALESCE(publish_at,created_at) DESC,id DESC LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",page*size).query(BasicItem.class).list().stream().map(this::withImages).toList();
        long total=jdbc.sql("SELECT count(*) FROM announcements"+where).query(Long.class).single();
        return new Page(items,total,page,size);
    }

    @Transactional public Item save(String employee,long actor, Save command) {
        if(command.images()!=null && command.images().size()>5) throw new IllegalArgumentException("每条公告最多 5 张图片");
        String state=command.publishAt()!=null && command.publishAt().isAfter(Instant.now()) ? "scheduled" : "published";
        long id=jdbc.sql("INSERT INTO announcements(title,content,content_format,publication_status,is_pinned,publish_at,expire_at,published_at,created_by,updated_by) VALUES(:title,:content,:format,:state,:pinned,:publishAt,:expireAt,CASE WHEN :state='published' THEN now() END,:actor,:actor) RETURNING id")
                .param("title",command.title()).param("content",command.content()).param("format",command.contentFormat()==null?"plain":command.contentFormat())
                .param("state",state).param("pinned",command.pinned()).param("publishAt",command.publishAt()).param("expireAt",command.expireAt()).param("actor",actor).query(Long.class).single();
        if(command.images()!=null) for(int i=0;i<command.images().size();i++) {
            Image image=command.images().get(i);
            if(image.filename()==null||image.filename().isBlank())throw new IllegalArgumentException("公告图片文件名不能为空");
            if(!List.of("image/png","image/jpeg").contains(image.mimeType()))throw new IllegalArgumentException("公告图片仅支持 PNG 或 JPEG");
            byte[] content=decode(image.base64Data());
            if(content.length==0 || content.length>5*1024*1024)throw new IllegalArgumentException("公告图片大小必须在 1 字节到 5MB 之间");
            String key=image.objectKey()==null||image.objectKey().isBlank()?"announcement/"+UUID.randomUUID():image.objectKey();
            jdbc.sql("INSERT INTO announcement_images(announcement_id,object_key,original_filename,mime_type,byte_size,sort_order,uploaded_by,content) VALUES(:id,:key,:name,:mime,:bytes,:sort,:actor,:content)")
                    .param("id",id).param("key",key).param("name",image.filename()).param("mime",image.mimeType()).param("bytes",content.length).param("sort",i+1).param("actor",actor).param("content",content).update();
        }
        audit.record(employee,"announcements","publish","announcement",String.valueOf(id),Map.of("status",state,"pinned",command.pinned(),"images",command.images()==null?0:command.images().size()));
        return find(id);
    }
    @Transactional public Item withdraw(String employee,long actor,long id) {
        int changed=jdbc.sql("UPDATE announcements SET publication_status='withdrawn',withdrawn_at=now(),updated_at=now(),updated_by=:actor,revision=revision+1 WHERE id=:id AND publication_status<>'withdrawn'")
                .param("actor",actor).param("id",id).update();
        if(changed==0) throw new java.util.NoSuchElementException("公告不存在或已撤回");
        audit.record(employee,"announcements","withdraw","announcement",String.valueOf(id));
        return find(id);
    }
    public ImageContent image(long id){return jdbc.sql("SELECT mime_type,content FROM announcement_images WHERE id=:id AND content IS NOT NULL").param("id",id).query((rs,n)->new ImageContent(rs.getString(1),rs.getBytes(2))).single();}
    private Item find(long id){return withImages(jdbc.sql("SELECT id,title,content,content_format,publication_status,is_pinned pinned,publish_at,expire_at,published_at,withdrawn_at FROM announcements WHERE id=:id").param("id",id).query(BasicItem.class).single());}
    private Item withImages(BasicItem item){List<ImageView> images=jdbc.sql("SELECT id,original_filename filename,mime_type,byte_size,sort_order FROM announcement_images WHERE announcement_id=:id ORDER BY sort_order").param("id",item.id()).query(ImageView.class).list();return new Item(item.id(),item.title(),item.content(),item.contentFormat(),item.publicationStatus(),item.pinned(),item.publishAt(),item.expireAt(),item.publishedAt(),item.withdrawnAt(),images);}
    private byte[] decode(String value){if(value==null)return new byte[0];int comma=value.indexOf(',');String payload=comma>=0?value.substring(comma+1):value;try{return Base64.getDecoder().decode(payload);}catch(IllegalArgumentException exception){throw new IllegalArgumentException("公告图片编码无效");}}
    public record Save(String title,String content,String contentFormat,boolean pinned,Instant publishAt,Instant expireAt,List<Image> images){}
    public record Image(String objectKey,String filename,String mimeType,String base64Data){}
    private record BasicItem(long id,String title,String content,String contentFormat,String publicationStatus,boolean pinned,Instant publishAt,Instant expireAt,Instant publishedAt,Instant withdrawnAt){}
    public record ImageView(long id,String filename,String mimeType,int byteSize,int sortOrder){public String url(){return "/api/v1/announcement-images/"+id;}}
    public record ImageContent(String mimeType,byte[] content){}
    public record Item(long id,String title,String content,String contentFormat,String publicationStatus,boolean pinned,Instant publishAt,Instant expireAt,Instant publishedAt,Instant withdrawnAt,List<ImageView> images){}
    public record Page(List<Item> items,long total,int page,int size){}
}
