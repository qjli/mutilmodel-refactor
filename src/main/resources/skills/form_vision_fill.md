# form_vision_fill

从营业执照、许可证等影像回填右侧表单。`form_patch` 键名须为 **camelCase**，与前端 `Form.Item name` 一致；宿主会用白名单归一化别名键。

## form_patch 键（节选）

**工商主体**：`companyName`（禁止 enterpriseName/company_name）、`companyShortName`、`formerName`、`legalRepresentative`、`enterpriseNature`、`enterpriseType`、`businessScope`、`companyPhone`、`companyEmail`、`registrationDate`（ISO）、`registeredRegion`、`registeredAddressDetail`、`actualLocation`、`registeredZip`、`registeredCapital`、`companyFax`、`learnChannel`、`unifiedSocialCreditCode`、`qualificationIdDocType`、`qualificationIdDocNumber`（身份证公民身份号码）。

**危化经营 / 安全生产类**（`safety*`）：`safetyAdminLicenseName`、`safetyLicenseNo`、`safetyLicenseValidityMode`（`fixed`|`long`）、`safetyLicenseValidityRange`（`fixed` 时为 `[start,end]` ISO 数组）、`safetyIssuingAuthority`、`safetyLegalRepresentative`（证面「主要负责人」也映射到此键）。

**道路危运**（`transport*`）：`transportAdminLicenseName`、`transportLicenseNo`、`transportLicenseValidityMode`、`transportLicenseValidityRange`、`transportIssuingAuthority`、`transportLegalRepresentative`。

原则：有把握才写入；读不清勿猜。勿自造 `issuingAuthority`、`issueDate`、`validityPeriodStart` 等未列键。

## 多主体歧义（强制）

多张营业执照，或营业执照与许可证上**企业名称/业户名称**指向**不同主体**（非简称包含关系）时，对冲突键（含 `companyName`、`unifiedSocialCreditCode` 及两证不一致的工商单行字段）：

1. **不得**写入 `form_patch`；
2. 在 `ambiguities` 中逐字段给出 `field_key`、`question_for_user`（中性）、`options`（`option_id`、`label` 标明证照来源、`suggested_value`）；
3. `reply`【待确认 / 歧义】与 `ambiguities` 一致，**禁止**在【已抽取字段】罗列多套主体却不给歧义。

同一主体：营业执照名称与许可证「企业名称/业户名称」一致时，`companyName` 与 `safety*`/`transport*` 可并存，不造歧义。

## reply 骨架（按序，无内容写「无。」）

1. **【识别概要】** — 证照类型与张数或用户意图。  
2. **【已抽取字段】** — 与 `form_patch` 逐行对应；键可用中文 + 可选括号 camelCase。  
3. **【待确认 / 歧义】** — 仅罗列候选，禁止「建议选…」「推荐…」。  
4. **【未覆盖 / 说明】** — 未写入 patch 的原因。

禁止 Markdown 代码围栏、JSON、图片链接。本技能不定义 `upload_guide`。

## 自检

- `form_patch` 每键是否在【已抽取字段】有一行？  
- 多主体时冲突键是否已省略并进入 `ambiguities`？
