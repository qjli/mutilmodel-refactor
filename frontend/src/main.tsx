import { App as AntdApp, ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import React from "react";
import ReactDOM from "react-dom/client";
import MultimodalConsole from "./App";
import "./index.css";

/** 与政务/企业填报门户一致的深红主色（参考界面） */
const brandPrimary = "#A8232A";
/** 次要操作：上传、链接类 */
const linkBlue = "#1677FF";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: brandPrimary,
          colorLink: linkBlue,
          colorInfo: linkBlue,
          borderRadius: 6,
          colorBorder: "#D9D9D9",
          colorBorderSecondary: "#E8E8E8",
          fontFamily:
            '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif',
        },
        components: {
          Form: {
            labelColor: "rgba(0,0,0,0.65)",
            verticalLabelPadding: "0 0 6px",
          },
          Input: {
            activeBorderColor: brandPrimary,
            hoverBorderColor: brandPrimary,
            colorBgContainer: "#ffffff",
          },
          Button: {
            primaryShadow: "0 2px 0 rgba(168, 35, 42, 0.06)",
          },
          Select: {
            optionSelectedBg: "rgba(168, 35, 42, 0.06)",
            selectorBg: "#ffffff",
          },
          DatePicker: {
            colorBgContainer: "#ffffff",
          },
        },
      }}
    >
      <AntdApp>
        <MultimodalConsole />
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
);
