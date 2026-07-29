package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_log")
public class InspectionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long vocabId;
    private Integer result;
    private LocalDateTime inspectionTime;
}
