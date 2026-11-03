package com.thinkfirst.vault;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
/** 每个用户的备用接口独立加密；接口地址只由服务器管理员配置，避免任意内网请求。 */
@Entity @Table(name="tf_model_connection",uniqueConstraints=@UniqueConstraint(columnNames={"ownerId","provider"}))
@Getter @Setter
public class ModelConnection {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long ownerId;
 private String provider;
 /** 升级已有数据库时默认关闭首选，避免旧记录的空值无法映射到布尔字段。 */
 @Column(nullable=false,columnDefinition="boolean default false")
 private boolean preferred;
 @com.fasterxml.jackson.annotation.JsonIgnore @Column(length=2048) private String cipher;
 private String mask;
}
