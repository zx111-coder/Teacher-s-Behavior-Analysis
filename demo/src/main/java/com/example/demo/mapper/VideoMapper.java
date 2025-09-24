package com.example.demo.mapper;

import com.example.demo.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoMapper {
    int insert(Video video);
    Video findById(Long id);
    List<Video> selectAll();
    int update(Video video);
    int deleteById(Long id);
    Video findByPath(@Param("path") String path);
    Video findTaskIdbyUrl(String videoUrl);
    Video findByTaskId(String taskId);

    @Select("select id,video_name,task_id,original_video_url,processed_video_url,created_at,status from Videos where user_id=#{userId}")
    List<Video> findByUserId(Long userId);
}
