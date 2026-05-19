import "./App.css";
import {
  BulbOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  FileProtectOutlined,
  FormOutlined,
  LoadingOutlined,
  MessageOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Badge,
  Button,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  Progress,
  Radio,
  Row,
  Select,
  Space,
  Tooltip,
  Typography,
} from "antd";
import dayjs from "dayjs";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import {
  augmentVisionFormPatchForHostClears,
  normalizeVisionUploadGuide,
  postJson,
  postVisionFormStream,
  serializeFormContextForVision,
  resolveSampleImageUrl,
  sanitizeAssistantReplyDisplay,
  sanitizeVisionAmbiguities,
  type VisionAmbiguousField,
  type VisionUploadGuide,
} from "./api";
import { AppHeader } from "./components/AppHeader";

type ChatResponse = {
  reply: string;
  formPatch?: Record<string, unknown>;
  serverTime: string;
  uploadGuide?: VisionUploadGuide | null;
};

/** 与后端 `FormVisionPatchNormalizer.CANONICAL_KEYS` / 工商整族键一致；未在此声明的键勿用于 `Form.Item name`。 */
type FormValues = {
  companyName: string;
  companyShortName: string;
  formerName?: string;
  enterpriseNature?: string;
  enterpriseType?: string;
  businessScope?: string;
  companyPhone?: string;
  companyEmail?: string;
  registrationDate?: dayjs.Dayjs;
  registeredRegion?: string;
  registeredAddressDetail?: string;
  actualLocation?: string;
  registeredZip?: string;
  registeredCapital?: string;
  companyFax?: string;
  learnChannel?: string;
  /** 营业执照「法定代表人」证面姓名（与企业资质区块法人身份证可并存） */
  legalRepresentative?: string;
  /** 企业资质信息 */
  unifiedSocialCreditCode?: string;
  qualificationIdDocType?: string;
  qualificationIdDocNumber?: string;
  /** 专业资质 — 安全生产相关 */
  safetyAdminLicenseName?: string;
  safetyLicenseNo?: string;
  safetyLicenseValidityMode?: "fixed" | "long";
  /** 固定日期模式下：许可有效期起止 */
  safetyLicenseValidityRange?: [dayjs.Dayjs, dayjs.Dayjs];
  safetyIssuingAuthority?: string;
  safetyLegalRepresentative?: string;
  /** 专业资质 — 危化品运输 */
  transportAdminLicenseName?: string;
  transportLicenseNo?: string;
  transportLicenseValidityMode?: "fixed" | "long";
  transportLicenseValidityRange?: [dayjs.Dayjs, dayjs.Dayjs];
  transportIssuingAuthority?: string;
  transportLegalRepresentative?: string;
};

const FORM_STORAGE_PREFIX = "multimodal-demo:form:";

const FORM_RANGE_KEYS = [
  "safetyLicenseValidityRange",
  "transportLicenseValidityRange",
] as const;

const LEGACY_SINGLE_DATE_TO_RANGE: Record<string, string> = {
  safetyLicenseValidityDate: "safetyLicenseValidityRange",
  transportLicenseValidityDate: "transportLicenseValidityRange",
};

/** 证面中文日期与 ISO；避免 Invalid Dayjs 导致 DatePicker 渲染崩溃。 */
function registrationDateStringToDayjs(s: string): dayjs.Dayjs | null {
  const t = s.trim();
  if (!t) {
    return null;
  }
  let d = dayjs(t);
  if (d.isValid()) {
    return d;
  }
  const cn = /^(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日?$/.exec(t);
  if (cn) {
    d = dayjs(`${cn[1]}-${cn[2].padStart(2, "0")}-${cn[3].padStart(2, "0")}`);
    if (d.isValid()) {
      return d;
    }
  }
  return null;
}

type VisionJobState = {
  status: "running" | "done" | "error";
  phase: string;
  done: number;
  total: number;
  label?: string;
  fileName?: string;
  thinkingLog: string;
  assistantLog: string;
  errorMessage?: string;
};

/** 本轮上传图片的本地预览（ObjectURL），组件卸载时统一 revoke。 */
type VisionThumbnailSlot = {
  name: string;
  previewUrl: string;
};

type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  pending?: boolean;
  vision?: VisionJobState;
  /** 与 vision 配套：独立进度卡上的缩略图与文件名 */
  visionThumbnails?: VisionThumbnailSlot[];
  /** 视觉识别完成后可选：结构化材料清单卡（upload_guide） */
  uploadGuide?: VisionUploadGuide;
  createdAt: number;
};

function normalizeVisionFormPatch(patch: Record<string, unknown>): Partial<FormValues> {
  const out = { ...patch } as Record<string, unknown>;
  const reg = out.registrationDate;
  if (typeof reg === "string" && reg) {
    const d = registrationDateStringToDayjs(reg);
    if (d) {
      out.registrationDate = d;
    } else {
      delete out.registrationDate;
    }
  }
  for (const key of FORM_RANGE_KEYS) {
    const val = out[key];
    if (
      Array.isArray(val) &&
      val.length === 2 &&
      typeof val[0] === "string" &&
      typeof val[1] === "string"
    ) {
      const d0 = dayjs(val[0]);
      const d1 = dayjs(val[1]);
      if (d0.isValid() && d1.isValid()) {
        out[key] = [d0, d1];
      }
    }
  }
  return out as Partial<FormValues>;
}

/** 歧义 field_key 与表单 name 的已知别名（模型偶发 enterpriseName 等）。 */
const AMBIGUITY_FIELD_KEY_ALIASES: Record<string, string> = {
  enterpriseName: "companyName",
  businessName: "companyName",
};

/** 将模型/后端可能返回的 field_key 转为与 Form.Item name 一致的 camelCase（与后端 camelCase 键一致）。 */
function normalizeVisionAmbiguityFieldKey(raw: string): string {
  let k = (raw ?? "").trim();
  if (!k) return k;
  if (k.includes("_")) {
    k = k.replace(/_([a-zA-Z0-9])/g, (_, ch: string) => ch.toUpperCase());
  }
  return AMBIGUITY_FIELD_KEY_ALIASES[k] ?? k;
}

/** 与后端 FormVisionMultiEntityConflictDetector.CLEAR_FIELD_SENTINEL 一致。 */
const VISION_CLEAR_FIELD_SENTINEL = "__CLEAR_FIELD__";

function resolveAmbiguityOptionValue(opt: VisionAmbiguousField["options"][number]): string {
  const v = opt.suggested_value;
  if (typeof v === "string" && v.trim() !== "") return v;
  const l = opt.label;
  if (typeof l === "string" && l.trim() !== "") return l.trim();
  return String(opt.option_id ?? "");
}

/** 从歧义 label / suggested_value 中抠出 ISO 日期片段（避免整段 label 传给 DatePicker 导致崩溃）。 */
function extractIsoDateFromAmbiguityText(s: string): string | null {
  const m = s.match(/\d{4}-\d{1,2}-\d{1,2}/);
  return m ? m[0] : null;
}

/** 歧义点选后写入表单的值：`registrationDate` 必须经 dayjs 化，其它键保持字符串。 */
function patchFromAmbiguityChoice(
  formKey: string,
  opt: VisionAmbiguousField["options"][number],
): Partial<FormValues> | null {
  const raw = opt.suggested_value;
  if (typeof raw === "string" && raw === VISION_CLEAR_FIELD_SENTINEL) {
    if (formKey === "registrationDate") {
      return { registrationDate: undefined } as Partial<FormValues>;
    }
    return { [formKey]: "" } as Partial<FormValues>;
  }
  const resolved = resolveAmbiguityOptionValue(opt);
  if (formKey === "registrationDate") {
    const iso = extractIsoDateFromAmbiguityText(resolved);
    if (!iso) {
      return null;
    }
    return normalizeVisionFormPatch({ registrationDate: iso }) as Partial<FormValues>;
  }
  return { [formKey]: resolved } as Partial<FormValues>;
}

/** 同一语义字段合并为一组选项，避免重复 key 与 field_key 与表单 name 不一致。 */
function normalizeVisionAmbiguities(list: VisionAmbiguousField[]): VisionAmbiguousField[] {
  const merged = new Map<string, VisionAmbiguousField>();
  for (const a of list) {
    const k = normalizeVisionAmbiguityFieldKey(a.field_key);
    const cur = merged.get(k);
    if (!cur) {
      merged.set(k, { ...a, field_key: k });
      continue;
    }
    const seen = new Set(cur.options.map((o) => String(o.option_id)));
    const more: typeof a.options = [];
    for (const o of a.options) {
      const id = String(o.option_id);
      if (!seen.has(id)) {
        seen.add(id);
        more.push(o);
      }
    }
    merged.set(k, {
      ...cur,
      options: [...cur.options, ...more],
      question_for_user:
        more.length > 0
          ? `${cur.question_for_user}\n${a.question_for_user}`
          : cur.question_for_user,
    });
  }
  return [...merged.values()];
}

/** 顶部进度条：不以磁盘读取为进度；准备阶段占位，进入 infer 后随流式推理/说明长度增长。 */
function visionProgressPercent(v: VisionJobState): number {
  if (v.status === "done") {
    return 100;
  }
  if (v.status === "error") {
    return v.phase === "infer" ? 88 : 12;
  }
  if (v.phase === "infer") {
    const streamLen = v.thinkingLog.length + v.assistantLog.length;
    const extra = Math.floor(streamLen / 100);
    return Math.min(96, 10 + Math.min(86, extra));
  }
  // load_image 等：仅表示「已提交、尚未进入模型流」，不与 done/total 挂钩
  if (v.phase === "load_image") {
    return 6;
  }
  return 8;
}

function visionStreamWeight(v: VisionJobState): number {
  return v.thinkingLog.length + v.assistantLog.length;
}

/**
 * 缩略图状态：准备阶段一律「待分析」；infer 后按流式输出量模拟「识别推进」（多图一次综合调用，无真实逐张 API 进度）。
 */
function visionModelSlotStatus(
  index: number,
  v: VisionJobState,
  slotCount: number,
): "queued" | "reading" | "done" | "failed" {
  const n = Math.max(1, slotCount);
  if (v.status === "error") {
    if (v.phase === "infer") {
      const base = inferSlotFromStream(index, n, visionStreamWeight(v));
      if (base === "reading") return "failed";
      return base;
    }
    return "failed";
  }
  if (v.status === "done" || v.phase === "done") {
    return "done";
  }
  if (v.phase === "load_image") {
    return "queued";
  }
  if (v.phase === "infer" && v.status === "running") {
    return inferSlotFromStream(index, n, visionStreamWeight(v));
  }
  return "queued";
}

function inferSlotFromStream(
  index: number,
  n: number,
  streamLen: number,
): "queued" | "reading" | "done" {
  if (streamLen < 24) {
    return index === 0 ? "reading" : "queued";
  }
  const span = Math.max(280, Math.floor(2200 / n));
  const boundary = Math.min(n, streamLen / span);
  const doneBelow = Math.floor(boundary);
  if (index < doneBelow) return "done";
  if (index === doneBelow && doneBelow < n) return "reading";
  return "queued";
}

function truncateFileName(name: string, max = 14) {
  if (name.length <= max) return name;
  return `${name.slice(0, max - 1)}…`;
}

function visionThumbCardHint(v: VisionJobState): string | null {
  if (v.status !== "running") {
    return null;
  }
  if (v.phase === "load_image") {
    return "正在提交图像至模型（磁盘读取不计入识别进度）";
  }
  if (v.phase === "infer") {
    if (v.label && !v.label.includes("读取第")) {
      return v.label;
    }
    return "模型正在综合分析已上传影像…";
  }
  return null;
}

type RailFocusKey = "chat" | "form" | "enterpriseQual" | "professionalQual";

function RailSegmentDots({ count }: { count: number }) {
  return (
    <div className="app-rail__dots" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className="app-rail__dot" />
      ))}
    </div>
  );
}

function formatClock(ts: number) {
  return new Date(ts).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

/** 材料清单卡（方案 C）：视觉识别气泡与纯文本气泡共用。 */
function UploadGuideCardSection({ guide }: { guide: VisionUploadGuide }) {
  return (
    <section className="vision-upload-guide" aria-label="材料清单与补传引导">
      <div className="vision-upload-guide__head">
        <CloudUploadOutlined className="vision-upload-guide__icon" aria-hidden />
        <Typography.Text strong className="vision-upload-guide__title">
          {guide.card_title ?? "材料清单"}
        </Typography.Text>
      </div>
      {guide.satisfied_labels.length > 0 ? (
        <div className="vision-upload-guide__satisfied">
          <CheckCircleOutlined className="vision-upload-guide__check" aria-hidden />
          <Typography.Text type="secondary">
            已上传 / 已识别：{guide.satisfied_labels.join("、")}
          </Typography.Text>
        </div>
      ) : null}
      {guide.missing_items.length > 0 ? (
        <div className="vision-upload-guide__grid">
          {guide.missing_items.map((mat) => (
            <div key={`ug-${mat.sample_image_id}`} className="vision-upload-guide__card">
              <div
                className="vision-upload-guide__thumb"
                title="图示为样证扫描件，仅作版式参考，非任何真实主体证照"
              >
                <img
                  className="vision-upload-guide__thumb-img"
                  src={resolveSampleImageUrl(mat.sample_image_id)}
                  alt=""
                  loading="lazy"
                />
                <span className="vision-upload-guide__sample-seal" aria-hidden="true">
                  样图
                </span>
              </div>
              <div className="vision-upload-guide__meta">
                <Typography.Text strong className="vision-upload-guide__card-title">
                  {mat.title}
                </Typography.Text>
                {mat.subtitle ? (
                  <Typography.Text type="secondary" className="vision-upload-guide__card-sub">
                    {mat.subtitle}
                  </Typography.Text>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export default function MultimodalConsole() {
  const { message, notification } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const safetyLicenseValidityMode = Form.useWatch("safetyLicenseValidityMode", form);
  const transportLicenseValidityMode = Form.useWatch("transportLicenseValidityMode", form);
  const sessionId = useMemo(() => crypto.randomUUID(), []);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      content:
        "欢迎使用多模态智能填报助手。左侧为对话流，右侧为结构化表单；文本对话可抽取字段。底部「上传文件」支持一次多选图片：将走 AgentScope 视觉 + Skill + 会话持久化 + 结构化输出，并在对话中显示读取进度、思考流与汇总；若模型列出歧义项，请在表单上方按提示点选确认。",
      createdAt: Date.now(),
    },
  ]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [visionBusy, setVisionBusy] = useState(false);
  const [ambiguities, setAmbiguities] = useState<VisionAmbiguousField[]>([]);
  const [railFocus, setRailFocus] = useState<RailFocusKey>("chat");
  const chatSectionRef = useRef<HTMLElement | null>(null);
  const formSectionRef = useRef<HTMLElement | null>(null);
  const enterpriseQualSectionRef = useRef<HTMLDivElement | null>(null);
  const professionalQualSectionRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const pageRootRef = useRef<HTMLDivElement | null>(null);
  const headerShellRef = useRef<HTMLDivElement | null>(null);
  const messagesRef = useRef(messages);
  messagesRef.current = messages;

  useEffect(() => {
    return () => {
      for (const m of messagesRef.current) {
        m.visionThumbnails?.forEach((t) => URL.revokeObjectURL(t.previewUrl));
      }
    };
  }, []);

  useLayoutEffect(() => {
    const root = pageRootRef.current;
    const slot = headerShellRef.current;
    if (!root || !slot) {
      return;
    }
    const apply = () => {
      const h = Math.ceil(slot.getBoundingClientRect().height);
      root.style.setProperty("--app-header-offset", `${h}px`);
    };
    apply();
    const ro = new ResizeObserver(apply);
    ro.observe(slot);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const raw = localStorage.getItem(FORM_STORAGE_PREFIX + sessionId);
    if (!raw) {
      return;
    }
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      for (const [legacyKey, rangeKey] of Object.entries(LEGACY_SINGLE_DATE_TO_RANGE)) {
        const legacy = parsed[legacyKey];
        if (legacy != null && parsed[rangeKey] == null) {
          const d = typeof legacy === "string" ? dayjs(legacy) : null;
          if (d?.isValid()) {
            parsed[rangeKey] = [d.toISOString(), d.toISOString()];
          }
          delete parsed[legacyKey];
        }
      }
      const reg = parsed.registrationDate;
      if (typeof reg === "string" && reg) {
        const d = registrationDateStringToDayjs(reg);
        if (d) {
          parsed.registrationDate = d;
        } else {
          delete parsed.registrationDate;
        }
      }
      for (const key of FORM_RANGE_KEYS) {
        const val = parsed[key];
        if (
          Array.isArray(val) &&
          val.length === 2 &&
          typeof val[0] === "string" &&
          typeof val[1] === "string"
        ) {
          parsed[key] = [dayjs(val[0]), dayjs(val[1])];
        }
      }
      form.setFieldsValue(parsed as FormValues);
    } catch {
      /* ignore */
    }
  }, [form, sessionId]);

  const persistForm = useCallback(() => {
    const v = form.getFieldsValue(true) as Record<string, unknown>;
    const serializable: Record<string, unknown> = { ...v };
    const rd = serializable.registrationDate;
    if (rd && dayjs.isDayjs(rd)) {
      serializable.registrationDate = (rd as dayjs.Dayjs).toISOString();
    }
    for (const key of FORM_RANGE_KEYS) {
      const val = serializable[key];
      if (
        Array.isArray(val) &&
        val.length === 2 &&
        dayjs.isDayjs(val[0]) &&
        dayjs.isDayjs(val[1])
      ) {
        serializable[key] = [
          (val[0] as dayjs.Dayjs).toISOString(),
          (val[1] as dayjs.Dayjs).toISOString(),
        ];
      }
    }
    delete serializable.safetyLicenseValidityDate;
    delete serializable.transportLicenseValidityDate;
    localStorage.setItem(
      FORM_STORAGE_PREFIX + sessionId,
      JSON.stringify(serializable),
    );
  }, [form, sessionId]);

  const onPickFiles = () => {
    if (visionBusy) {
      message.warning("图片识别进行中，请稍候再上传");
      return;
    }
    fileInputRef.current?.click();
  };

  const onFileInputChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) {
      return;
    }
    const now = Date.now();
    const listText =
      files.length === 1
        ? `上传图片：${files[0].name}`
        : `已上传 ${files.length} 张图片`;
    const visionThumbnails: VisionThumbnailSlot[] = files.map((f) => ({
      name: f.name && f.name.trim() ? f.name : "image",
      previewUrl: URL.createObjectURL(f),
    }));
    const userMsgId = crypto.randomUUID();
    const assistantId = crypto.randomUUID();

    setVisionBusy(true);
    setMessages((m) => [
      ...m,
      { id: userMsgId, role: "user", content: listText, createdAt: now },
      {
        id: assistantId,
        role: "assistant",
        content: "",
        createdAt: now,
        visionThumbnails,
        vision: {
          status: "running",
          phase: "load_image",
          done: 0,
          total: files.length,
          thinkingLog: "",
          assistantLog: "",
        },
      },
    ]);

    const patchVision = (fn: (v: VisionJobState) => VisionJobState) => {
      setMessages((m) =>
        m.map((row) =>
          row.id === assistantId && row.vision ? { ...row, vision: fn(row.vision) } : row,
        ),
      );
    };

    const formContextJson = serializeFormContextForVision(
      form.getFieldsValue(true) as Record<string, unknown>,
    );

    try {
      await postVisionFormStream(sessionId, files, (ev) => {
        if (ev.type === "progress") {
          patchVision((v) => ({
            ...v,
            phase: ev.phase,
            done: ev.done,
            total: ev.total,
            label: ev.label,
            fileName: ev.fileName,
          }));
        } else if (ev.type === "thinking") {
          patchVision((v) => ({ ...v, thinkingLog: v.thinkingLog + ev.delta }));
        } else if (ev.type === "assistant_text") {
          patchVision((v) => ({ ...v, assistantLog: v.assistantLog + ev.delta }));
        } else if (ev.type === "result") {
          const ambRaw = sanitizeVisionAmbiguities(ev.ambiguities);
          const ambNormalized = normalizeVisionAmbiguities(ambRaw);
          const ambiguityFieldKeys = ambNormalized.map((a) =>
            normalizeVisionAmbiguityFieldKey(a.field_key),
          );
          const mergedPatch = augmentVisionFormPatchForHostClears(
            ev.formPatch ?? {},
            {
              multi_enterprise_conflict_applied: ev.multi_enterprise_conflict_applied,
              multi_transport_conflict_applied: ev.multi_transport_conflict_applied,
              multi_safety_conflict_applied: ev.multi_safety_conflict_applied,
            },
            ambiguityFieldKeys,
          );
          const patch = normalizeVisionFormPatch(mergedPatch);
          form.setFieldsValue(patch);
          persistForm();
          setAmbiguities(ambNormalized);
          const uploadGuide = normalizeVisionUploadGuide(ev.uploadGuide);
          setMessages((m) =>
            m.map((row) =>
              row.id === assistantId
                ? {
                    ...row,
                    content: ev.reply ?? "",
                    uploadGuide,
                    vision: row.vision
                      ? {
                          ...row.vision,
                          status: "done",
                          phase: "done",
                          done: row.vision.total,
                          label: "已完成",
                        }
                      : undefined,
                  }
                : row,
            ),
          );
        } else if (ev.type === "error") {
          setMessages((m) =>
            m.map((row) =>
              row.id === assistantId
                ? {
                    ...row,
                    content: `识别出错：${ev.message}`,
                    vision: row.vision
                      ? {
                          ...row.vision,
                          status: "error",
                          errorMessage: ev.message,
                          phase: "error",
                        }
                      : undefined,
                  }
                : row,
            ),
          );
        }
      }, undefined, formContextJson);
    } catch (err) {
      setMessages((m) =>
        m.map((row) =>
          row.id === assistantId
            ? {
                ...row,
                content: `请求失败：${err instanceof Error ? err.message : String(err)}`,
                vision: row.vision
                  ? {
                      ...row.vision,
                      status: "error",
                      errorMessage: err instanceof Error ? err.message : String(err),
                      phase: "error",
                    }
                  : undefined,
              }
            : row,
        ),
      );
      notification.error({
        message: "图片识别失败",
        description: err instanceof Error ? err.message : String(err),
      });
    } finally {
      setVisionBusy(false);
    }
  };

  const sendChat = async () => {
    const text = draft.trim();
    if (!text || sending) {
      return;
    }
    const now = Date.now();
    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: text,
      createdAt: now,
    };
    const pendingId = crypto.randomUUID();
    setMessages((m) => [
      ...m,
      userMsg,
      { id: pendingId, role: "assistant", content: "", pending: true, createdAt: now },
    ]);
    setDraft("");
    setSending(true);
    try {
      const data = await postJson<ChatResponse>(
        `/api/sessions/${encodeURIComponent(sessionId)}/messages`,
        { content: text },
      );
      if (data.formPatch) {
        form.setFieldsValue(normalizeVisionFormPatch(data.formPatch));
        persistForm();
      }
      const uploadGuide = normalizeVisionUploadGuide(data.uploadGuide);
      setMessages((m) =>
        m.map((row) =>
          row.id === pendingId
            ? {
                ...row,
                content: data.reply,
                pending: false,
                createdAt: Date.now(),
                uploadGuide,
              }
            : row,
        ),
      );
    } catch (e) {
      setMessages((m) => m.filter((row) => row.id !== pendingId));
      notification.error({
        message: "发送失败",
        description: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setSending(false);
    }
  };

  const onSubmitForm = async () => {
    try {
      await form.validateFields();
      message.success("校验通过（演示：未调用真实提交接口）");
    } catch {
      message.warning("请修正表单标红项后再提交");
    }
  };

  const onSaveDraft = () => {
    persistForm();
    message.success("草稿已暂存到本地");
  };

  const scrollToSection = (target: RailFocusKey) => {
    setRailFocus(target);
    const el =
      target === "chat"
        ? chatSectionRef.current
        : target === "form"
          ? formSectionRef.current
          : target === "enterpriseQual"
            ? enterpriseQualSectionRef.current
            : professionalQualSectionRef.current;
    el?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  /** 将最后一条助手消息滚入对话流可视区域；输出已落定时同时切换到「智能对话」并滚主区到对话列。 */
  useLayoutEffect(() => {
    const last = messages[messages.length - 1];
    if (!last || last.role !== "assistant") {
      return;
    }

    const assistantOutputSettled =
      !last.pending &&
      (!last.vision ||
        last.vision.status === "done" ||
        last.vision.status === "error");

    if (assistantOutputSettled) {
      setRailFocus("chat");
      chatSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    const frame = requestAnimationFrame(() => {
      const el = document.getElementById(`chat-msg-${last.id}`);
      el?.scrollIntoView({
        behavior:
          last.pending || last.vision?.status === "running" ? "auto" : "smooth",
        block: "end",
        inline: "nearest",
      });
    });
    return () => cancelAnimationFrame(frame);
  }, [messages]);

  const railEl = (
    <aside className="app-rail app-rail--timeline" aria-label="功能导航">
      <nav className="app-rail__track">
        <div className="app-rail__line" aria-hidden />
        <div className="app-rail__stack">
          <div className="app-rail__stop">
            <Tooltip title="智能对话" placement="right">
              <button
                type="button"
                className={`app-rail__btn ${railFocus === "chat" ? "app-rail__btn--active" : "app-rail__btn--idle"}`}
                onClick={() => scrollToSection("chat")}
                aria-current={railFocus === "chat" ? "true" : undefined}
              >
                <MessageOutlined />
              </button>
            </Tooltip>
          </div>
          <RailSegmentDots count={3} />
          <div className="app-rail__stop">
            <Tooltip title="表单填报（企业基本信息）" placement="right">
              <button
                type="button"
                className={`app-rail__btn ${railFocus === "form" ? "app-rail__btn--active" : "app-rail__btn--idle"}`}
                onClick={() => scrollToSection("form")}
                aria-current={railFocus === "form" ? "true" : undefined}
              >
                <FormOutlined />
              </button>
            </Tooltip>
          </div>
          <RailSegmentDots count={2} />
          <div className="app-rail__stop">
            <Tooltip title="企业资质信息" placement="right">
              <button
                type="button"
                className={`app-rail__btn ${railFocus === "enterpriseQual" ? "app-rail__btn--active" : "app-rail__btn--idle"}`}
                onClick={() => scrollToSection("enterpriseQual")}
                aria-current={railFocus === "enterpriseQual" ? "true" : undefined}
              >
                <FileProtectOutlined />
              </button>
            </Tooltip>
          </div>
          <RailSegmentDots count={2} />
          <div className="app-rail__stop">
            <Tooltip title="专业资质信息" placement="right">
              <button
                type="button"
                className={`app-rail__btn ${railFocus === "professionalQual" ? "app-rail__btn--active" : "app-rail__btn--idle"}`}
                onClick={() => scrollToSection("professionalQual")}
                aria-current={railFocus === "professionalQual" ? "true" : undefined}
              >
                <SafetyCertificateOutlined />
              </button>
            </Tooltip>
          </div>
        </div>
      </nav>
    </aside>
  );

  return (
    <>
      {typeof document !== "undefined" ? createPortal(railEl, document.body) : null}
      <div className="page-root" ref={pageRootRef}>
      <div ref={headerShellRef} className="page-header-slot">
        <AppHeader sessionId={sessionId} />
      </div>

      <div className="page-shell">
        <div className="page-main" id="main-content">
        <div className="page-main-inner">
          <div className="main-grid">
            <section
              ref={chatSectionRef}
              className="panel chat-column"
            >
              <header className="panel-header">
                <span className="chat-header-title">智能对话</span>
              </header>
              <div className="chat-stream">
                {messages.map((item) => {
                  const visionThumbs = item.visionThumbnails;
                  const isVisionAssistant =
                    item.role === "assistant" &&
                    item.vision &&
                    visionThumbs &&
                    visionThumbs.length > 0;

                  if (isVisionAssistant) {
                    const v = item.vision!;
                    const pct = visionProgressPercent(v);
                    const showResultBody =
                      v.status === "running"
                        ? v.assistantLog.trim().length > 0
                        : v.status === "done" || v.status === "error";
                    const resultText =
                      v.status === "done" && item.content
                        ? item.content
                        : v.status === "error"
                          ? item.content
                          : v.assistantLog;
                    const thumbHint = visionThumbCardHint(v);

                    return (
                      <div
                        key={item.id}
                        id={`chat-msg-${item.id}`}
                        className="msg-row msg-assistant msg-assistant--vision"
                      >
                        <div className="msg-assistant-vision-wrap">
                          <div className="vision-thumb-card" aria-label="模型识别进度">
                            <div className="vision-thumb-card__head">
                              <Typography.Text strong className="vision-thumb-card__title">
                                模型识别进度
                              </Typography.Text>
                              <Progress
                                percent={pct}
                                status={
                                  v.status === "error"
                                    ? "exception"
                                    : v.status === "done"
                                      ? "success"
                                      : "active"
                                }
                                size="small"
                                showInfo
                              />
                            </div>
                            <div
                              className="vision-thumb-row"
                              style={{
                                gridTemplateColumns: `repeat(${visionThumbs.length}, minmax(0, 1fr))`,
                              }}
                            >
                              {visionThumbs.map((thumb, idx) => {
                                const st = visionModelSlotStatus(idx, v, visionThumbs.length);
                                return (
                                  <div
                                    key={`${item.id}-thumb-${idx}`}
                                    className={`vision-thumb-cell vision-thumb-cell--${st}`}
                                  >
                                    <div className="vision-thumb-frame">
                                      <img src={thumb.previewUrl} alt="" loading="lazy" />
                                      {st === "reading" ? (
                                        <div className="vision-thumb-scan" aria-hidden />
                                      ) : null}
                                      {st === "done" ? (
                                        <span className="vision-thumb-badge" aria-label="已纳入分析">
                                          <CheckCircleOutlined />
                                        </span>
                                      ) : null}
                                      {st === "failed" ? (
                                        <span className="vision-thumb-badge vision-thumb-badge--fail" aria-label="失败">
                                          !
                                        </span>
                                      ) : null}
                                    </div>
                                    <div className="vision-thumb-name" title={thumb.name}>
                                      {truncateFileName(thumb.name)}
                                    </div>
                                    <div className="vision-thumb-state">
                                      {st === "done" ? (
                                        <>
                                          <CheckCircleOutlined /> 已分析
                                        </>
                                      ) : st === "reading" ? (
                                        <>
                                          <LoadingOutlined spin /> 识别中
                                        </>
                                      ) : st === "failed" ? (
                                        "已中断"
                                      ) : (
                                        "排队"
                                      )}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                            {thumbHint ? (
                              <Typography.Text type="secondary" className="vision-thumb-card__hint">
                                {thumbHint}
                              </Typography.Text>
                            ) : null}
                          </div>

                          <div className="msg-stack msg-stack--vision-follow">
                            <div className="msg-meta">
                              <span>助手</span>
                              <span>{formatClock(item.createdAt)}</span>
                              {v.status === "running" ? (
                                <Badge status="processing" text="识别中" />
                              ) : null}
                            </div>
                            <div className="msg-bubble msg-bubble--vision-split">
                              <section className="vision-thinking-panel" aria-label="推理过程">
                                <div className="vision-thinking-panel__head">
                                  <BulbOutlined className="vision-thinking-panel__icon" aria-hidden />
                                  <Typography.Text strong>推理过程</Typography.Text>
                                  <Typography.Text type="secondary" className="vision-thinking-panel__sub">
                                    模型中间推理（流式）
                                  </Typography.Text>
                                </div>
                                <pre className="vision-thinking-panel__body">
                                  {v.thinkingLog
                                    ? v.thinkingLog
                                    : v.status === "running"
                                      ? "等待模型推理片段…"
                                      : "（本轮无单独推理片段）"}
                                </pre>
                              </section>

                              <section className="vision-result-panel" aria-label="识别说明">
                                <Typography.Text strong className="vision-result-panel__head">
                                  识别说明
                                </Typography.Text>
                                <div className="vision-result-panel__body">
                                  {showResultBody ? (
                                    <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}>
                                      {sanitizeAssistantReplyDisplay(resultText)}
                                    </Typography.Paragraph>
                                  ) : v.status === "running" ? (
                                    <Typography.Text type="secondary">等待模型生成说明…</Typography.Text>
                                  ) : null}
                                </div>
                              </section>

                              {v.status === "done" && item.uploadGuide ? (
                                <UploadGuideCardSection guide={item.uploadGuide} />
                              ) : null}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  }

                  return (
                    <div
                      key={item.id}
                      id={`chat-msg-${item.id}`}
                      className={`msg-row msg-${item.role}`}
                    >
                      <div className="msg-stack">
                        <div className="msg-meta">
                          <span>{item.role === "user" ? "用户" : "助手"}</span>
                          <span>{formatClock(item.createdAt)}</span>
                          {item.pending ? (
                            <Badge status="processing" text="生成中" />
                          ) : null}
                        </div>
                        <div
                          className={`msg-bubble ${item.pending ? "msg-bubble--pending" : ""}`}
                        >
                          {item.pending ? (
                            <div className="typing" aria-label="生成中">
                              <span>正在回复</span>
                              <span className="typing-dot" />
                              <span className="typing-dot" />
                              <span className="typing-dot" />
                            </div>
                          ) : (
                            <>
                              <div className="msg-bubble-text">
                                {sanitizeAssistantReplyDisplay(item.content)}
                              </div>
                              {item.uploadGuide ? (
                                <UploadGuideCardSection guide={item.uploadGuide} />
                              ) : null}
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            <section
              ref={formSectionRef}
              className="panel form-panel form-column"
            >
              <div className="panel-body">
                <div className="form-toolbar">
                  <Typography.Title level={5} className="form-title">
                    企业基本信息
                  </Typography.Title>
                  <Space>
                    <Button onClick={onSaveDraft}>暂存草稿</Button>
                    <Button type="primary" onClick={onSubmitForm}>
                      确认提交
                    </Button>
                  </Space>
                </div>
                {ambiguities.length > 0 ? (
                  <Alert
                    type="warning"
                    showIcon
                    className="form-ambiguity-alert"
                    message="需您确认的内容（来自影像识别）"
                    description={
                      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                        {ambiguities.map((amb, ambIdx) => (
                          <div
                            key={`${normalizeVisionAmbiguityFieldKey(amb.field_key)}-${ambIdx}`}
                          >
                            <Typography.Paragraph style={{ marginBottom: 8 }}>
                              {amb.question_for_user}
                            </Typography.Paragraph>
                            <Radio.Group
                              onChange={(e) => {
                                const formKey = normalizeVisionAmbiguityFieldKey(amb.field_key);
                                const picked = String(e.target.value);
                                const opt = (amb.options ?? []).find(
                                  (o) => String(o.option_id) === picked,
                                );
                                if (opt) {
                                  const patch = patchFromAmbiguityChoice(formKey, opt);
                                  if (patch == null) {
                                    message.warning(
                                      "未能从选项中解析出有效日期，请在表单「注册日期」中手工选择。",
                                    );
                                    return;
                                  }
                                  form.setFieldsValue(patch);
                                  persistForm();
                                }
                                setAmbiguities((prev) =>
                                  prev.filter(
                                    (a) =>
                                      normalizeVisionAmbiguityFieldKey(a.field_key) !==
                                      formKey,
                                  ),
                                );
                              }}
                            >
                              <Space direction="vertical">
                                {(amb.options ?? []).map((o) => (
                                  <Radio key={String(o.option_id)} value={String(o.option_id)}>
                                    {o.label}
                                  </Radio>
                                ))}
                              </Space>
                            </Radio.Group>
                          </div>
                        ))}
                      </Space>
                    }
                  />
                ) : null}
                <Form<FormValues>
                  form={form}
                  layout="vertical"
                  requiredMark
                  initialValues={{
                    safetyLicenseValidityMode: "fixed",
                    transportLicenseValidityMode: "long",
                    qualificationIdDocType: "身份证",
                  }}
                  onValuesChange={() => persistForm()}
                >
                  <Row gutter={24}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="公司名称"
                        name="companyName"
                        rules={[{ required: true, message: "请输入公司名称" }]}
                      >
                        <Input placeholder="请输入公司名称" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="公司曾用名" name="formerName">
                        <Input placeholder="可选" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="公司简称"
                        name="companyShortName"
                        rules={[{ required: true, message: "请输入公司简称" }]}
                      >
                        <Input placeholder="请输入公司简称" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="法定代表人（工商证照）"
                        name="legalRepresentative"
                        tooltip="来自营业执照证面「法定代表人」；与下方资质证件号码（身份证人像面）可对应不同自然人"
                      >
                        <Input placeholder="可选，识别营业执照后回填" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label={
                          <Space size={4}>
                            <span>企业类型</span>
                            <Tooltip title="请选择与证照一致的企业类型">
                              <QuestionCircleOutlined style={{ color: "rgba(0,0,0,0.45)" }} />
                            </Tooltip>
                          </Space>
                        }
                        name="enterpriseType"
                        rules={[{ required: true, message: "请选择企业类型" }]}
                      >
                        <Select
                          placeholder="请选择"
                          allowClear
                          options={[
                            { value: "有限责任公司", label: "有限责任公司" },
                            { value: "股份有限公司", label: "股份有限公司" },
                            { value: "外商投资企业", label: "外商投资企业" },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="企业性质"
                        name="enterpriseNature"
                        rules={[{ required: true, message: "请选择企业性质" }]}
                      >
                        <Select
                          placeholder="请选择"
                          allowClear
                          options={[
                            { value: "国有", label: "国有" },
                            { value: "民营", label: "民营" },
                            { value: "混合所有制", label: "混合所有制" },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="注册日期"
                        name="registrationDate"
                        rules={[{ required: true, message: "请选择注册日期" }]}
                      >
                        <DatePicker style={{ width: "100%" }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="注册资本(万元)"
                        name="registeredCapital"
                        rules={[{ required: true, message: "请输入注册资本" }]}
                      >
                        <Input placeholder="单位：万元" allowClear />
                      </Form.Item>
                    </Col>
                    
                    <Col xs={24} md={12}>
                      <Form.Item
                        label="注册地邮编"
                        name="registeredZip"
                        rules={[{ required: true, message: "请输入邮编" }]}
                      >
                        <Input placeholder="6 位邮编" allowClear maxLength={6} />
                      </Form.Item>
                    </Col>
                    <Col span={24}>
                      <Form.Item label="注册地址（住所）" name="registeredAddressDetail">
                        <Input.TextArea
                          rows={2}
                          placeholder="与营业执照「住所」一致，可选"
                          allowClear
                        />
                      </Form.Item>
                    </Col>
                    <Col span={24}>
                      <Form.Item label="经营范围" name="businessScope">
                        <Input.TextArea
                          rows={3}
                          placeholder="与证照证面一致，可选"
                          allowClear
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="注册地行政区划" name="registeredRegion">
                        <Input placeholder="省 / 市 / 区县，可选" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="实际经营地址" name="actualLocation">
                        <Input placeholder="与证照不一致时填写，可选" allowClear />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={24}>
                    <Col xs={24} md={12}>
                      <Form.Item label="公司电话" name="companyPhone">
                        <Input placeholder="可选" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="公司邮箱" name="companyEmail">
                        <Input placeholder="可选" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="公司传真" name="companyFax">
                        <Input placeholder="可选" allowClear />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="获知渠道" name="learnChannel">
                        <Input placeholder="可选" allowClear />
                      </Form.Item>
                    </Col>
                  </Row>

                  <div
                    ref={enterpriseQualSectionRef}
                    id="section-enterprise-qual"
                    className="form-scroll-anchor"
                  >
                  <Divider className="form-section-divider" />
                  <Typography.Title level={5} className="form-section-title">
                    企业资质信息
                  </Typography.Title>
                  <Row gutter={24}>
                    <Col span={24}>
                      <Form.Item
                        label="统一社会信用代码"
                        name="unifiedSocialCreditCode"
                        rules={[{ required: true, message: "请输入统一社会信用代码" }]}
                      >
                        <Input placeholder="请输入 18 位统一社会信用代码" allowClear maxLength={18} />
                      </Form.Item>
                    </Col>
                    <Col span={24}>
                      <Form.Item
                        label="证件类型与号码"
                        required
                        tooltip="左侧为证件类型，右侧为证件号码"
                      >
                        <Space.Compact style={{ width: "100%" }}>
                          <Form.Item
                            name="qualificationIdDocType"
                            noStyle
                            rules={[{ required: true, message: "请选择证件类型" }]}
                          >
                            <Select
                              placeholder="证件类型"
                              style={{ width: 120 }}
                              options={[
                                { value: "身份证", label: "身份证" },
                                { value: "护照", label: "护照" },
                                { value: "港澳居民来往内地通行证", label: "港澳居民来往内地通行证" },
                              ]}
                            />
                          </Form.Item>
                          <Form.Item
                            name="qualificationIdDocNumber"
                            noStyle
                            rules={[{ required: true, message: "请输入证件号码" }]}
                          >
                            <Input style={{ width: "100%" }} placeholder="请输入证件号码" allowClear />
                          </Form.Item>
                        </Space.Compact>
                      </Form.Item>
                    </Col>
                  </Row>
                  </div>

                  <div
                    ref={professionalQualSectionRef}
                    id="section-professional-qual"
                    className="form-scroll-anchor"
                  >
                  <Divider className="form-section-divider" />
                  <Typography.Title level={5} className="form-section-title">
                    专业资质信息
                  </Typography.Title>
                  <Typography.Paragraph type="secondary" className="form-section-lead">
                    请按许可证件如实填写下列字段；许可证件中的企业名称应与营业执照保持一致。资质扫描件可通过底部对话区上传，由助手协助处理。
                  </Typography.Paragraph>

                  <div className="form-subsection">
                    <div className="form-subsection__head">
                    
                      <Typography.Text strong className="form-subsection__title">
                        危险品经营许可证
                      </Typography.Text>
                    </div>
                    <Row gutter={16} className="form-qualification-row">
                      <Col xs={24} lg={12}>
                        <Form.Item
                          label="行政许可名称"
                          name="safetyAdminLicenseName"
                          rules={[{ required: true, message: "请填写相关行政许可名称" }]}
                        >
                          <Input placeholder="请填写相关行政许可名称" allowClear />
                        </Form.Item>
                        <Form.Item
                          label="编号"
                          name="safetyLicenseNo"
                          rules={[{ required: true, message: "请填写编号" }]}
                        >
                          <Input placeholder="请填写编号" allowClear />
                        </Form.Item>
                        <Form.Item label="有效期" required>
                          <Form.Item name="safetyLicenseValidityMode" noStyle>
                            <Radio.Group>
                              <Radio.Button value="fixed">固定日期</Radio.Button>
                              <Radio.Button value="long">长期有效</Radio.Button>
                            </Radio.Group>
                          </Form.Item>
                          <div className="form-nested-field">
                            <Form.Item
                              name="safetyLicenseValidityRange"
                              noStyle
                              rules={
                                (safetyLicenseValidityMode ?? "fixed") === "fixed"
                                  ? [
                                      { required: true, message: "请选择有效期起止日期" },
                                      {
                                        validator: async (_, value) => {
                                          if (
                                            !value ||
                                            !Array.isArray(value) ||
                                            value.length !== 2 ||
                                            !value[0] ||
                                            !value[1]
                                          ) {
                                            return Promise.reject(new Error("请选择完整的起止日期"));
                                          }
                                          return Promise.resolve();
                                        },
                                      },
                                    ]
                                  : []
                              }
                            >
                              <DatePicker.RangePicker
                                style={{ width: "100%", marginTop: 8 }}
                                placeholder={["开始日期", "结束日期"]}
                                disabled={(safetyLicenseValidityMode ?? "fixed") === "long"}
                              />
                            </Form.Item>
                          </div>
                        </Form.Item>
                      </Col>
                      <Col xs={24} lg={12}>
                        <Form.Item
                          label="发证机构"
                          name="safetyIssuingAuthority"
                          rules={[{ required: true, message: "请填写发证机构" }]}
                        >
                          <Input placeholder="请填写发证机构" allowClear />
                        </Form.Item>
                        <Form.Item
                          label="法定代表人（负责人）"
                          name="safetyLegalRepresentative"
                          rules={[{ required: true, message: "请填写法定代表人（负责人）" }]}
                        >
                          <Input placeholder="请填写法定代表人（负责人）" allowClear />
                        </Form.Item>
                      </Col>
                    </Row>
                  </div>

                  <div className="form-subsection form-subsection--spaced">
                    <Typography.Text strong className="form-subsection__title">
                      危化品运输许可证
                    </Typography.Text>
                    <Row gutter={16} className="form-qualification-row">
                      <Col xs={24} lg={12}>
                        <Form.Item label="行政许可名称" name="transportAdminLicenseName">
                          <Input placeholder="请填写相关行政许可名称" allowClear />
                        </Form.Item>
                        <Form.Item label="编号" name="transportLicenseNo">
                          <Input placeholder="请填写编号" allowClear />
                        </Form.Item>
                        <Form.Item label="有效期">
                          <Form.Item name="transportLicenseValidityMode" noStyle>
                            <Radio.Group>
                              <Radio.Button value="fixed">固定日期</Radio.Button>
                              <Radio.Button value="long">长期有效</Radio.Button>
                            </Radio.Group>
                          </Form.Item>
                          <div className="form-nested-field">
                            <Form.Item
                              name="transportLicenseValidityRange"
                              noStyle
                              rules={
                                (transportLicenseValidityMode ?? "long") === "fixed"
                                  ? [
                                      { required: true, message: "请选择有效期起止日期" },
                                      {
                                        validator: async (_, value) => {
                                          if (
                                            !value ||
                                            !Array.isArray(value) ||
                                            value.length !== 2 ||
                                            !value[0] ||
                                            !value[1]
                                          ) {
                                            return Promise.reject(new Error("请选择完整的起止日期"));
                                          }
                                          return Promise.resolve();
                                        },
                                      },
                                    ]
                                  : []
                              }
                            >
                              <DatePicker.RangePicker
                                style={{ width: "100%", marginTop: 8 }}
                                placeholder={["开始日期", "结束日期"]}
                                disabled={(transportLicenseValidityMode ?? "long") === "long"}
                              />
                            </Form.Item>
                          </div>
                        </Form.Item>
                      </Col>
                      <Col xs={24} lg={12}>
                        <Form.Item label="发证机构" name="transportIssuingAuthority">
                          <Input placeholder="请填写发证机构" allowClear />
                        </Form.Item>
                        <Form.Item name="transportLegalRepresentative" hidden preserve>
                          <Input />
                        </Form.Item>
                      </Col>
                    </Row>
                  </div>
                  </div>
                </Form>
              </div>
            </section>
          </div>
        </div>
        </div>
      </div>

      <footer className="composer-dock">
        <input
          ref={fileInputRef}
          type="file"
          className="visually-hidden"
          accept="image/*"
          multiple
          disabled={visionBusy}
          onChange={(e) => void onFileInputChange(e)}
        />
        <div className="composer-dock-inner">
          <Input.TextArea
            className="composer-textarea"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                void sendChat();
              }
            }}
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            autoSize={{ minRows: 1, maxRows: 5 }}
            disabled={sending}
          />
          <div className="composer-actions">
            <Tooltip title="支持一次选择多个文件">
              <Button
                type="link"
                className="composer-upload-link"
                icon={<CloudUploadOutlined />}
                onClick={onPickFiles}
              >
                上传文件
              </Button>
            </Tooltip>
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={sending}
              onClick={() => void sendChat()}
            >
              发送
            </Button>
          </div>
        </div>
      </footer>
    </div>
    </>
  );
}
