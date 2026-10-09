# SogaKey 輸入法

SogaKey 是我自己做的免費 Android 輸入法，以 [Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android) 為基礎，目標是：**Gboard 般順手的注音打字，加上 AI 語音輸入與翻譯**。

## 下載

到 [Releases 頁面](https://github.com/word-light/fcitx5-android1/releases/tag/latest) 下載，每次更新都會自動編譯並覆蓋同一個版本。

| 檔案 | 用途 | 必裝？ |
|---|---|---|
| `1-SogaKey-main.apk` | 主程式 | 必裝 |
| `2-SogaKey-zhuyin-plugin.apk` | 注音（新酷音） | 要打注音就裝 |
| `3-SogaKey-korean-plugin.apk` | 韓文（Hangul） | 要打韓文就裝 |
| `4-SogaKey-japanese-plugin.apk` | 日文（Anthy） | 要打日文就裝 |

先裝 1，再裝需要的外掛。只更新主程式時，不必重裝外掛；外掛有改動才需要重裝。

## 功能

- **注音鍵盤**：版面與手感貼近 Gboard，聲調鍵、符號、表情符號建議（偏向 Z 世代用語與常用表情）
- **AI 語音輸入**：麥克風鍵一按就說，辨識後由 Gemini 整理成通順文字；可一鍵翻成英文、日文、韓文或中文再送出
  - 需自備金鑰：Gemini（必填）、Groq（選填，語音辨識更快）
  - 金鑰只存在你的手機裡
- **手寫輸入**：麥克風旁有筆形按鈕，手寫板精簡，不佔太多畫面
- **日文**：羅馬拼音鍵盤，以及仿 Samsung 的 12 鍵滑動（フリック）鍵盤，按下會展開十字選字視窗
- **韓文**：Hangul 鍵盤
- **倉頡**：只顯示手機字型能顯示的字，避免出現空白方框
- **快速切英文**：點地球鍵，在英文與目前輸入法之間來回切換；長按地球鍵可選其他輸入法

## 設定小提示

- 想改回「依序切換所有輸入法」：設定 → 輸入法 → 語言切換鍵行為
- 日文要在「輸入法」新增「日本語 (Anthy)」才會出現
- AI 語音的金鑰在輸入法的 AI 語音設定頁填寫

## 開發狀態

目前仍在持續開發，部分功能尚未在更多手機上驗證。已知待辦：日文詞庫擴充、鍵盤背景圖與顏色。歡迎回報問題。

## 授權與致謝

本專案是 [Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android) 的衍生作品，沿用其授權（LGPL-2.1-or-later）。感謝 Fcitx5、新酷音（libchewing）、Anthy、libhangul 等開源專案。
