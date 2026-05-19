const BASE = "";

export type ProblemBody = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
};

export async function readApiError(res: Response): Promise<string> {
  const text = await res.text();
  try {
    const j = JSON.parse(text) as ProblemBody;
    if (j.detail) {
      return j.detail;
    }
    if (j.title) {
      return j.title;
    }
  } catch {
    /* ignore */
  }
  return text || res.statusText;
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(await readApiError(res));
  }
  return (await res.json()) as T;
}

export async function postMultipart<T>(path: string, file: File): Promise<T> {
  const fd = new FormData();
  fd.append("file", file);
  const res = await fetch(`${BASE}${path}`, { method: "POST", body: fd });
  if (!res.ok) {
    throw new Error(await readApiError(res));
  }
  return (await res.json()) as T;
}

export async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) {
    throw new Error(await readApiError(res));
  }
  return (await res.json()) as T;
}

/** SSE payload keys follow backend JSON (snake_case for nested DTO fields). */
export type VisionAmbiguousOption = {
  option_id: string;
  label: string;
  suggested_value: string;
};

export type VisionAmbiguousField = {
  field_key: string;
  question_for_user: string;
  options: VisionAmbiguousOption[];
};

/** 与后端 `upload_guide` / SSE `uploadGuide` 嵌套字段一致（Jackson snake_case）。 */
export type VisionUploadGuideItem = {
  sample_image_id: string;
  title: string;
  subtitle?: string;
};

export type VisionUploadGuide = {
  card_title?: string;
  satisfied_labels: string[];
  missing_items: VisionUploadGuideItem[];
};

/** 仅四种示意图，与技能 `upload_guide_dialog` 白名单一致。 */
const SAMPLE_IMAGE_PATHS: Record<string, string> = {
  BUSINESS_LICENSE: "/samples/business-license.png",
  ID_CARD_FRONT: "/samples/id-card-front.png",
  ROAD_TRANSPORT_PERMIT: "/samples/road-transport-permit.png",
  SAFETY_PRODUCTION_PERMIT: "/samples/safety-production-permit.png",
};

/** 与 `sample_image_id` 对应的中文全称（卡片 title 与展示用，防模型发散）。 */
const CANONICAL_MATERIAL_TITLE: Record<keyof typeof SAMPLE_IMAGE_PATHS, string> = {
  BUSINESS_LICENSE: "营业执照",
  ID_CARD_FRONT: "身份证人像面",
  ROAD_TRANSPORT_PERMIT: "道路危险货物运输许可证",
  SAFETY_PRODUCTION_PERMIT: "危险化学品经营许可证",
};

const MATERIAL_TITLE_SET = new Set<string>(Object.values(CANONICAL_MATERIAL_TITLE));

const DEFAULT_ONBOARDING_SUBTITLE: Record<keyof typeof SAMPLE_IMAGE_PATHS, string> = {
  BUSINESS_LICENSE: "企业登记主体信息",
  ID_CARD_FRONT: "法人或经办人身份核验",
  ROAD_TRANSPORT_PERMIT: "危化品道路运输资质",
  SAFETY_PRODUCTION_PERMIT: "安全生产与危化经营许可信息",
};

/**
 * 展示层兜底：去掉模型偶发的 Markdown 裂图、「（如适用）」等，避免正文出现无意义链接。
 */
export function sanitizeAssistantReplyDisplay(raw: string): string {
  if (!raw) {
    return raw;
  }
  return raw
    .replace(/!\[[^\]]*\]\([^)]*\)/g, "")
    .replace(/[（(]\s*如适用\s*[）)]/g, "")
    .replace(/[（(]\s*若适用\s*[）)]/g, "")
    .replace(/[（(]\s*视情况\s*[）)]/g, "")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function dedupeMissingBySampleId(items: VisionUploadGuideItem[]): VisionUploadGuideItem[] {
  const seen = new Set<string>();
  const out: VisionUploadGuideItem[] = [];
  for (const it of items) {
    if (seen.has(it.sample_image_id)) {
      continue;
    }
    seen.add(it.sample_image_id);
    out.push(it);
  }
  return out;
}

/**
 * 标题像「总览 / 首次说明 / 四证示意」：模型常把此类场景的 {@code card_title} 写成「所需上传证照」等，
 * 但 {@code missing_items} 只填一条；此前端补全与技能 {@code upload_guide_dialog} 对齐。
 */
function titleImpliesFourSampleOnboarding(cardTitle: string | undefined): boolean {
  const t = (cardTitle ?? "").trim();
  if (!t) {
    return false;
  }
  return (
    /如何使用|怎么用|使用说明|怎么上传|如何上传|初次|首次|新手指南/.test(t) ||
    /请上传以下/.test(t) ||
    /以下(文件|材料|证照|图片|照片)/.test(t) ||
    /下列(文件|材料|证照)/.test(t) ||
    (t.includes("下列") && t.includes("所需")) ||
    /上传指引|示意图|样例|四证|四类|四种证件|请传照片/.test(t) ||
    (/材料清单/.test(t) && !/仍缺|仅缺/.test(t)) ||
    /所需上传|须上传|应上传|请上传(的)?证照|证照示意|证照样例|证照类型|上传.*证照/.test(t) ||
    /^所需/.test(t)
  );
}

/** 是否应补全为四种证照（首次说明、或模型重复同一类、或总览标题漏写条目）。 */
function shouldExpandToFourSamples(
  cardTitle: string | undefined,
  items: VisionUploadGuideItem[],
): boolean {
  const t = (cardTitle ?? "").trim();
  const uniq = new Set(items.map((i) => i.sample_image_id));
  /** 模型把多条写成同一 sample_image_id（如两张营业执照）。 */
  if (items.length >= 2 && uniq.size === 1) {
    return true;
  }
  if (items.length >= 4) {
    return false;
  }
  /** 有总览类标题但 missing 为空：仍铺四宫格（模型漏写数组）。 */
  if (items.length === 0) {
    return titleImpliesFourSampleOnboarding(cardTitle);
  }

  /**
   * 标题像「仍缺某某证」且只列 1 条时，多为真缺一件，勿扩成四卡。
   * 「请上传以下文件」等虽只有一个 missing，但是总览口吻，应扩成四卡。
   */
  const looksLikeSingleGap =
    items.length === 1 &&
    /仍缺|仅缺|还需补充|尚缺|补传/.test(t) &&
    !/下列|以下|所需证照|四种|四类|请上传以下|示意图|样例|所需上传|上传指引/.test(t);
  if (looksLikeSingleGap) {
    return false;
  }

  const howToInTitle =
    /如何使用|怎么用|使用说明|怎么上传|如何上传|初次|首次|新手指南/.test(t);
  const listStyleOnboarding = titleImpliesFourSampleOnboarding(cardTitle);

  if ((howToInTitle || listStyleOnboarding) && items.length < 4) {
    return true;
  }
  /**
   * 仅 1 条且为合法四 id 之一：多为「如何使用/所需上传」场景下模型漏写其余三项（标题未必含上述关键词）。
   */
  if (
    items.length === 1 &&
    [...uniq].every((id) => id in SAMPLE_IMAGE_PATHS)
  ) {
    return true;
  }
  return false;
}

/**
 * 补全为四种示意卡片：先保留模型已给出的条目顺序，再补上缺失的 id（补项按 id 字母序追加，与技能「顺序不限」一致）。
 */
function buildFourCanonicalMissing(existing: VisionUploadGuideItem[]): VisionUploadGuideItem[] {
  const byId = new Map(existing.map((m) => [m.sample_image_id, m]));
  const allIds = (Object.keys(SAMPLE_IMAGE_PATHS) as (keyof typeof SAMPLE_IMAGE_PATHS)[]).sort((a, b) =>
    a.localeCompare(b),
  );
  const ordered: (keyof typeof SAMPLE_IMAGE_PATHS)[] = [];
  for (const it of existing) {
    if (!ordered.includes(it.sample_image_id)) {
      ordered.push(it.sample_image_id);
    }
  }
  for (const id of allIds) {
    if (!ordered.includes(id)) {
      ordered.push(id);
    }
  }
  return ordered.map((id) => ({
    sample_image_id: id,
    title: CANONICAL_MATERIAL_TITLE[id],
    subtitle: byId.get(id)?.subtitle ?? DEFAULT_ONBOARDING_SUBTITLE[id],
  }));
}

/** 废弃代号 → 现用四选一（兼容旧模型输出）。 */
const LEGACY_SAMPLE_IMAGE_ID: Record<string, keyof typeof SAMPLE_IMAGE_PATHS> = {
  HAZMAT_PERMIT: "SAFETY_PRODUCTION_PERMIT",
  GENERIC_LICENSE: "BUSINESS_LICENSE",
};

function canonicalSampleImageId(raw: string): keyof typeof SAMPLE_IMAGE_PATHS {
  let id = (raw ?? "").trim().toUpperCase().replace(/-/g, "_") as string;
  const mapped = LEGACY_SAMPLE_IMAGE_ID[id];
  if (mapped) {
    id = mapped;
  }
  if (id in SAMPLE_IMAGE_PATHS) {
    return id as keyof typeof SAMPLE_IMAGE_PATHS;
  }
  return "BUSINESS_LICENSE";
}

/** 将模型返回的 `sample_image_id` 映射为 `public/samples` 下 PNG 示意缩略图（统一在 CSS 中适配尺寸）。 */
export function resolveSampleImageUrl(sampleImageId: string): string {
  return SAMPLE_IMAGE_PATHS[canonicalSampleImageId(sampleImageId)];
}

/** 校验并规整 SSE {@code uploadGuide}，无有效内容时返回 {@code undefined}。 */
export function normalizeVisionUploadGuide(raw: unknown): VisionUploadGuide | undefined {
  if (raw == null || typeof raw !== "object") {
    return undefined;
  }
  const o = raw as Record<string, unknown>;
  const rawMissing = o.missing_items;
  const rawSat = o.satisfied_labels;
  const satisfied_labels = Array.isArray(rawSat)
    ? rawSat
        .filter((x): x is string => typeof x === "string" && x.trim() !== "")
        .map((s) => s.trim())
        .filter((s) => MATERIAL_TITLE_SET.has(s))
    : [];
  const card_title =
    typeof o.card_title === "string" && o.card_title.trim() !== "" ? o.card_title.trim() : undefined;
  let missing_items: VisionUploadGuideItem[] = Array.isArray(rawMissing)
    ? rawMissing
        .filter((x): x is Record<string, unknown> => x != null && typeof x === "object" && !Array.isArray(x))
        .map((it) => {
          const sidRaw = String(it.sample_image_id ?? "").trim();
          const sid = canonicalSampleImageId(sidRaw);
          const subtitle =
            typeof it.subtitle === "string" && it.subtitle.trim() !== "" ? it.subtitle.trim() : undefined;
          return {
            sample_image_id: sid,
            title: CANONICAL_MATERIAL_TITLE[sid],
            subtitle,
          };
        })
    : [];

  missing_items = dedupeMissingBySampleId(missing_items);
  const blockOnboardingExpand =
    satisfied_labels.length > 0 &&
    missing_items.length > 0 &&
    missing_items.length < 4;
  if (!blockOnboardingExpand && shouldExpandToFourSamples(card_title, missing_items)) {
    missing_items = buildFourCanonicalMissing(missing_items);
  }

  if (!card_title && missing_items.length === 0 && satisfied_labels.length === 0) {
    return undefined;
  }
  return { card_title, satisfied_labels, missing_items };
}

/** 须与后端 {@code FormVisionMultiEntityConflictDetector} 中 {@code ENTERPRISE_BASIC_KEYS} 顺序与拼写一致。 */
export const VISION_ENTERPRISE_FIELD_KEYS = [
  "companyName",
  "companyShortName",
  "formerName",
  "unifiedSocialCreditCode",
  "enterpriseNature",
  "enterpriseType",
  "registeredRegion",
  "registeredAddressDetail",
  "actualLocation",
  "registeredZip",
  "registrationDate",
  "registeredCapital",
  "businessScope",
  "legalRepresentative",
] as const;

/** 须与后端 {@code FormVisionMultiEntityConflictDetector.TRANSPORT_PERMIT_KEYS} 一致。 */
export const VISION_TRANSPORT_FIELD_KEYS = [
  "transportAdminLicenseName",
  "transportLicenseNo",
  "transportLicenseValidityMode",
  "transportLicenseValidityRange",
  "transportIssuingAuthority",
  "transportLegalRepresentative",
] as const;

/** 须与后端 {@code FormVisionMultiEntityConflictDetector.SAFETY_PERMIT_KEYS} 一致。 */
export const VISION_SAFETY_FIELD_KEYS = [
  "safetyAdminLicenseName",
  "safetyLicenseNo",
  "safetyLicenseValidityMode",
  "safetyLicenseValidityRange",
  "safetyIssuingAuthority",
  "safetyLegalRepresentative",
] as const;

export type VisionResultHostFlags = {
  multi_enterprise_conflict_applied?: boolean;
  multi_transport_conflict_applied?: boolean;
  multi_safety_conflict_applied?: boolean;
};

/**
 * 服务端已从 {@code form_patch} 剔除整族键时，前端仍保留旧 {@code Form} 状态；对「未出现在 patch 中的族内键」写入
 * {@code undefined} 以清空控件，避免与「需您确认」互斥。
 */
export function augmentVisionFormPatchForHostClears(
  patch: Record<string, unknown>,
  flags: VisionResultHostFlags,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...patch };
  const clearMissing = (keys: readonly string[]) => {
    for (const k of keys) {
      if (!Object.prototype.hasOwnProperty.call(out, k)) {
        out[k] = undefined;
      }
    }
  };
  if (flags.multi_enterprise_conflict_applied) {
    clearMissing(VISION_ENTERPRISE_FIELD_KEYS);
  }
  if (flags.multi_transport_conflict_applied) {
    clearMissing(VISION_TRANSPORT_FIELD_KEYS);
  }
  if (flags.multi_safety_conflict_applied) {
    clearMissing(VISION_SAFETY_FIELD_KEYS);
  }
  return out;
}

/** 防止模型/反序列化异常导致 {@code options} 缺失等运行时崩溃。 */
export function sanitizeVisionAmbiguities(raw: unknown): VisionAmbiguousField[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const out: VisionAmbiguousField[] = [];
  for (const item of raw) {
    if (item == null || typeof item !== "object" || Array.isArray(item)) {
      continue;
    }
    const o = item as Record<string, unknown>;
    const fk = typeof o.field_key === "string" ? o.field_key.trim() : "";
    const q = typeof o.question_for_user === "string" ? o.question_for_user : "";
    const rawOpts = o.options;
    if (!Array.isArray(rawOpts) || rawOpts.length === 0) {
      continue;
    }
    const options: VisionAmbiguousOption[] = [];
    for (const op of rawOpts) {
      if (op == null || typeof op !== "object" || Array.isArray(op)) {
        continue;
      }
      const p = op as Record<string, unknown>;
      options.push({
        option_id: String(p.option_id ?? ""),
        label: typeof p.label === "string" ? p.label : "",
        suggested_value:
          typeof p.suggested_value === "string"
            ? p.suggested_value
            : p.suggested_value != null && p.suggested_value !== undefined
              ? String(p.suggested_value)
              : "",
      });
    }
    if (!fk || options.length === 0) {
      continue;
    }
    out.push({ field_key: fk, question_for_user: q, options });
  }
  return out;
}

export type VisionSseEvent =
  | {
      type: "progress";
      phase: string;
      done: number;
      total: number;
      label?: string;
      fileName?: string;
    }
  | { type: "thinking"; delta: string }
  | { type: "assistant_text"; delta: string }
  | {
      type: "result";
      reply: string;
      formPatch: Record<string, unknown>;
      ambiguities: VisionAmbiguousField[];
      uploadGuide?: VisionUploadGuide | null;
    } & VisionResultHostFlags
  | { type: "done" }
  | { type: "error"; message: string };

async function consumeSseJson(
  body: ReadableStream<Uint8Array>,
  onEvent: (ev: VisionSseEvent) => void,
): Promise<void> {
  const reader = body.getReader();
  const dec = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += dec.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      for (const rawLine of block.split("\n")) {
        const line = rawLine.replace(/\r$/, "");
        if (!line.startsWith("data:")) {
          continue;
        }
        const json = line.slice(5).trim();
        if (!json) {
          continue;
        }
        try {
          onEvent(JSON.parse(json) as VisionSseEvent);
        } catch (e) {
          console.error("[vision-sse] bad json frame", e, json.slice(0, 240));
        }
      }
    }
  }
}

/**
 * Upload multiple images; consumes Server-Sent Events with JSON `data:` frames.
 */
export async function postVisionFormStream(
  sessionId: string,
  files: File[],
  onEvent: (ev: VisionSseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const fd = new FormData();
  for (const f of files) {
    fd.append("files", f);
  }
  const res = await fetch(
    `${BASE}/api/sessions/${encodeURIComponent(sessionId)}/vision/form-stream`,
    {
      method: "POST",
      body: fd,
      headers: { Accept: "text/event-stream" },
      signal,
    },
  );
  if (!res.ok) {
    throw new Error(await readApiError(res));
  }
  if (!res.body) {
    throw new Error("响应无流式正文");
  }
  await consumeSseJson(res.body, onEvent);
}
