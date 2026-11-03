package com.thinkfirst.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfirst.coach.CoachSession;
import com.thinkfirst.vault.ApiKeyService;
import org.springframework.stereotype.Service;
@Service
public class SlotGenerationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlotGenerationService.class);
    private final LlmProperties properties;
    private final ApiKeyService apiKeys;
    private final DeepSeekClient client;
    private final ObjectMapper mapper;
    private final LlmUsageService usage;
    public SlotGenerationService(LlmProperties properties, ApiKeyService apiKeys, DeepSeekClient client, ObjectMapper mapper, LlmUsageService usage) {
        this.properties=properties; this.apiKeys=apiKeys; this.client=client; this.mapper=mapper; this.usage=usage;
    }
    public void updateSlots(CoachSession session, java.util.Map<String,Object> applied, String answer) {
        if (!properties.isEnabled()) throw new IllegalStateException("请启用模型后生成需求");
        String key = apiKeys.requirePlaintext(session.getOwnerId());
        if (key == null) throw new IllegalStateException("请在设置中配置 DeepSeek Key");
        try {
            String prompt = "原始想法：" + session.getIdea()
                + "\n历史回答：" + mapper.writeValueAsString(session.getHistory())
                + "\n已确认信息：" + mapper.writeValueAsString(session.getSlots())
                + "\n最新回答：" + answer
                + "\n待填写字段结构：" + mapper.writeValueAsString(applied);
            DeepSeekClient.Completion result = client.complete(key,
                "你是需求分析Agent。面向普通用户写作，用日常中文，不用产品开发术语。根据用户想法和最新回答重新生成待填写字段的所有值。"
                + "只输出JSON，顶层键与提供结构完全一致，保持数据类型及嵌套结构。"
                + "结构中的示例不是用户事实，禁止沿用无关人物、吃药等案例。"
                + "框架字段 s10_framework 只能选择 S.C.A.F.F.、R.G.C.、故事化 Prompt、万能模板之一。复杂开发默认S.C.A.F.F.，简单任务用R.G.C.，表达用户处境用故事化，通用任务用万能模板。s10_prompt必须按所选框架分段填写本项目具体内容，并展开各组成部分；s11_techniques只记录提供示例、自查等技巧，不可把zero-shot、few-shot、CoT当成框架。"
                + "最新回答优先，所有用户、任务、功能、约束围绕当前项目动态更新。"
                + "未知事实写待确认，不能编造年龄、验证结果或使用经历。s1_scores 的 repeat/rule/verify/sensitive/fault 为0至5分，总分为五项之和，增加对应的 *_reason 字段逐项解释依据。敏感性越不敏感分越高，容错性越易纠错分越高。",
                prompt, 0.3, properties.getSlotsMaxTokens());
            java.util.Map<String,Object> updated;
            try { updated = ModelJsonValidator.parse(mapper,result.content(),applied); }
            catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
                // 最多修复一次；不向用户暴露原始回复，也不拼接可能含密钥的异常。
                result = client.complete(key,"你是结构化需求编辑。只输出合法JSON，不使用代码围栏。保留给定结构的所有键、嵌套字段及类型，不新增事实。",
                    prompt + "\n上次返回未通过结构校验，请按字段结构重新生成。s10_framework只能是S.C.A.F.F.、R.G.C.、故事化 Prompt、万能模板。",0.1,properties.getSlotsMaxTokens());
                updated = ModelJsonValidator.parse(mapper,result.content(),applied);
            }
            // 用量记录失败不应使已成功生成的内容整次失败。
            try { usage.record(session.getOwnerId(), "slots", session.getStage(), result); }
            catch (RuntimeException ignored) { log.warn("需求用量记录失败，账号={}，阶段={}", session.getOwnerId(), session.getStage()); }
            applied.clear(); applied.putAll(updated);
        } catch (Exception e) {
            String reference = java.util.UUID.randomUUID().toString().substring(0, 8);
            // 日志只保留错误类别与定位编号，不写用户原文、模型回复、密码或密钥。
            log.warn("需求生成失败：编号={}，账号={}，阶段={}，类型={}", reference, session.getOwnerId(), session.getStage(), e.getClass().getSimpleName());
            String reason = e instanceof LlmException ? e.getMessage()
                : e instanceof com.fasterxml.jackson.core.JsonProcessingException ? "AI返回的内容格式不正确"
                : e instanceof IllegalArgumentException ? "AI返回的需求字段不完整或类型不正确"
                : "服务处理异常";
            throw new IllegalStateException(reason + "，请重试（编号：" + reference + "）", e);
        }
    }

}
