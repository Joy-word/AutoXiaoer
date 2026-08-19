(function () {
  var STORAGE_KEY = "auto-xiaoer-language";
  var DEFAULT_LANGUAGE = "zh-CN";
  var SUPPORTED_LANGUAGES = ["zh-CN", "en"];

  var messages = {
    "zh-CN": {
      title: "Auto 小二 — 纯端侧智能体，住在手机里的伙伴",
      brandName: "Auto 小二",
      ogTitle: "Auto 小二",
      description: "Auto 小二是运行在 Android 上的开源智能体应用，用语言操控手机完成任务，支持定时、通知触发与微信远程触发。",
      ogDescription: "纯端侧智能体，住在手机里的伙伴。",
      navLabel: "页面导航",
      languageLabel: "语言选择",
      navFeatures: "功能优势",
      navHow: "快速上手",
      navScenarios: "使用场景",
      navShots: "界面一览",
      navContact: "联系我",
      navDownload: "立即下载",
      heroTagline: "纯端侧智能体，住在手机里的伙伴。",
      heroDownload: "下载 APK（Releases）",
      heroSource: "查看源码",
      heroVideo: "视频介绍",
      heroNote: "开源 · MIT · Android 7.0+ · 支持自有模型 API",
      featuresTitle: "为什么是「小二」",
      featuresLead: "像人一样通过界面理解与操作界面；主动计划未来任务；有自我迭代能力的成长型智能体。",
      feature1Title: "纯端侧运行",
      feature1Text: "App 独立完成规划与触控，无需通过电脑或 ADB 连线，出门在外同样可用。",
      feature2Title: "百变小二",
      feature2Text: "基于双 Agent + 人设 LLM 架构，自由定义人设，把「执行任务」与「对人说话」分得清清楚楚。",
      feature3Title: "视觉理解，真机操作",
      feature3Text: "看图识屏后再点击、滑动、输入，适配你手机里已安装的各类应用。",
      feature4Title: "定时与通知唤醒",
      feature4Text: "按日历重复执行预设任务；也可在指定 App 推送到达时自动开跑流水线。",
      feature5Title: "微信远程（ClawBot）",
      feature5Text: "扫码绑定后可从微信投递指令，也是你与小二的专属连接通道。",
      feature6Title: "OpenAI 兼容端点",
      feature6Text: "不同模块的模型可分别配置；支持多配置切换与自定义 Prompt。",
      howTitle: "三步上手",
      howLead: "从装好依赖到发出第一条自然语言任务，只需几分钟。详细图文见仓库 README。",
      step1Title: "开启无障碍或者使用 Shizuku",
      step1Text: "授权界面操作，让本机拥有执行点击、滑动等操作的能力。",
      step2Title: "安装 Auto 小二并授权",
      step2Text: "从 Releases 安装 APK，按引导打开悬浮窗、Shizuku、输入法等必要权限。",
      step3Title: "配置模型，开始说话",
      step3Text: "填入兼容 OpenAI 格式的 API；为「控制者」与「执行者」选好模型后即可下任务。",
      guideLink: "阅读完整安装与排错指南 →",
      scenariosTitle: "可以怎么玩",
      scenariosLead: "以下为能力示意，实际效果因机型、应用版本与模型而异。",
      scenario1Role: "你",
      scenario1Prompt: "「今天出门要带伞吗？」",
      scenario1Title: "本地自动化",
      scenario1Item1: "打开天气 APP，查询对应城市的天气",
      scenario1Item2: "回复天气与带伞建议",
      scenario1Caption: "自如地操作手机上的 APP，帮你获取想要的信息。",
      scenario2Role: "定时任务",
      scenario2Prompt: "「每天晚上 10:30 在“小二管家”群里提醒大家睡觉。」",
      scenario2Title: "到点执行",
      scenario2Item1: "支持一次性 / 重复规则",
      scenario2Item2: "可配合亮屏与后台策略",
      scenario2Caption: "把「记得去做」交给住在手机里的小二。",
      scenario3Role: "通知触发",
      scenario3Prompt: "「收到微信消息时，查看并回复。」",
      scenario3Title: "事件驱动",
      scenario3Item1: "监听指定包名通知",
      scenario3Item2: "触发预设事件",
      scenario3Caption: "小二也可以有自己的朋友圈。",
      scenario4Role: "微信 · ClawBot",
      scenario4Prompt: "「无聊的时候发一句“给我讲个笑话吧”」",
      scenario4Title: "远程执行",
      scenario4Item1: "扫码绑定后远程下指令",
      scenario4Item2: "手机端仍负责真实执行",
      scenario4Caption: "手机不在身边，但小二一直在。",
      shotsTitle: "界面一览",
      shotsLead: "Material Design 原生体验，悬浮窗实时看进度。",
      carouselRole: "轮播",
      carouselLabel: "应用界面截图轮播",
      carouselPrev: "上一张截图",
      carouselNext: "下一张截图",
      carouselDots: "选择截图",
      carouselHint: "点击图片可在新标签打开原图 · 移动端支持左右滑动切换",
      carouselViewport: "截图区域，按左右方向键切换",
      carouselPosition: "第 {current} 张，共 {total} 张",
      screenshotAlt: "应用截图 {number}",
      heroImageAlt: "应用截图预览",
      contactTitle: "联系我",
      contactLead: "加入交流群交流使用体验，也可以通过微信联系开发者。",
      contact1Title: "加入交流群",
      contact1Text: "扫码加入 Auto 小二交流群，和其他用户一起交流。",
      contact1Alt: "Auto 小二交流群二维码",
      contact2Title: "微信联系",
      contact2Text: "扫码添加微信，反馈问题或交流项目合作。",
      contact2Alt: "微信联系二维码",
      ctaTitle: "准备好让伙伴住进手机了吗？",
      ctaText: "开源可审计，欢迎提 Issue 与 PR 一起完善小二。",
      ctaDownload: "前往 Releases 下载",
      ctaJournal: "关注开发日记",
      footerLabel: "页脚链接",
      footerText: "Auto 小二 — Powered by Auto小二工作室"
    },
    en: {
      title: "Auto Xiao'er — Your On-Device AI Companion",
      brandName: "Auto Xiao'er",
      ogTitle: "Auto Xiao'er",
      description: "Auto Xiao'er is an open-source Android AI agent that operates your phone through natural language, with scheduled tasks, notification triggers, and remote WeChat commands.",
      ogDescription: "An on-device AI agent that lives on your phone.",
      navLabel: "Page navigation",
      languageLabel: "Language selection",
      navFeatures: "Features",
      navHow: "Get Started",
      navScenarios: "Use Cases",
      navShots: "Gallery",
      navContact: "Contact",
      navDownload: "Download",
      heroTagline: "An on-device AI agent that lives on your phone.",
      heroDownload: "Download APK (Releases)",
      heroSource: "View Source",
      heroVideo: "Watch Video",
      heroNote: "Open source · MIT · Android 7.0+ · Bring your own model API",
      featuresTitle: "Why Xiao'er?",
      featuresLead: "It understands and operates interfaces like a person, plans future tasks proactively, and improves through iteration.",
      feature1Title: "Fully On-Device",
      feature1Text: "The app handles planning and touch control independently, with no computer or ADB connection required.",
      feature2Title: "Your Own Xiao'er",
      feature2Text: "A dual-agent architecture with a persona LLM lets you shape its character while keeping task execution separate from conversation.",
      feature3Title: "Visual Understanding",
      feature3Text: "It reads the screen before tapping, swiping, or typing, and works with the apps already installed on your phone.",
      feature4Title: "Schedules and Triggers",
      feature4Text: "Run preset tasks on recurring schedules, or start workflows automatically when selected app notifications arrive.",
      feature5Title: "Remote via WeChat (ClawBot)",
      feature5Text: "Pair by scanning a QR code, then send instructions from WeChat through your private connection to Xiao'er.",
      feature6Title: "OpenAI-Compatible APIs",
      feature6Text: "Configure separate models for different modules, switch between profiles, and customize prompts.",
      howTitle: "Get Started in Three Steps",
      howLead: "Go from installing the required services to sending your first natural-language task in just a few minutes.",
      step1Title: "Enable Accessibility or Shizuku",
      step1Text: "Grant interface control so the device can perform taps, swipes, and other actions.",
      step2Title: "Install and Authorize Auto Xiao'er",
      step2Text: "Install the APK from Releases, then follow the guide to enable overlay, Shizuku, keyboard, and other required permissions.",
      step3Title: "Configure a Model and Start Talking",
      step3Text: "Enter an OpenAI-compatible API, choose models for the controller and executor, and start assigning tasks.",
      guideLink: "Read the full installation and troubleshooting guide →",
      scenariosTitle: "What Can It Do?",
      scenariosLead: "These examples illustrate its capabilities. Results vary by device, app version, and model.",
      scenario1Role: "You",
      scenario1Prompt: "“Should I take an umbrella today?”",
      scenario1Title: "Local Automation",
      scenario1Item1: "Open the weather app and check your city",
      scenario1Item2: "Reply with the forecast and advice",
      scenario1Caption: "It operates apps on your phone to find the information you need.",
      scenario2Role: "Scheduled Task",
      scenario2Prompt: "“Remind everyone in the Xiao'er group to sleep at 10:30 PM every day.”",
      scenario2Title: "Runs on Time",
      scenario2Item1: "One-time and recurring schedules",
      scenario2Item2: "Optional screen wake and background strategies",
      scenario2Caption: "Leave the remembering and the doing to the companion on your phone.",
      scenario3Role: "Notification Trigger",
      scenario3Prompt: "“When a WeChat message arrives, read it and reply.”",
      scenario3Title: "Event-Driven",
      scenario3Item1: "Listen for notifications from selected apps",
      scenario3Item2: "Trigger a preset event",
      scenario3Caption: "Xiao'er can take part in your online social life too.",
      scenario4Role: "WeChat · ClawBot",
      scenario4Prompt: "“When you are bored, send me a joke.”",
      scenario4Title: "Remote Execution",
      scenario4Item1: "Send remote instructions after pairing",
      scenario4Item2: "Your phone still performs the real actions",
      scenario4Caption: "Your phone may be elsewhere, but Xiao'er is still available.",
      shotsTitle: "Interface Gallery",
      shotsLead: "A native Material Design experience with real-time progress in a floating window.",
      carouselRole: "carousel",
      carouselLabel: "App screenshot carousel",
      carouselPrev: "Previous screenshot",
      carouselNext: "Next screenshot",
      carouselDots: "Choose a screenshot",
      carouselHint: "Open an image in a new tab · Swipe left or right on mobile",
      carouselViewport: "Screenshot area. Use the left and right arrow keys to navigate.",
      carouselPosition: "Image {current} of {total}",
      screenshotAlt: "App screenshot {number}",
      heroImageAlt: "App screenshot preview",
      contactTitle: "Contact",
      contactLead: "For feedback, support, or project collaboration, contact me by email.",
      contact1Title: "Join the Community",
      contact1Text: "Scan the code to join the Auto Xiao'er community and talk with other users.",
      contact1Alt: "Auto Xiao'er community QR code",
      contact2Title: "Contact on WeChat",
      contact2Text: "Scan the code to report an issue or discuss project collaboration.",
      contact2Alt: "Developer WeChat QR code",
      ctaTitle: "Ready to Put a Companion on Your Phone?",
      ctaText: "Open source and auditable. Issues and pull requests are welcome.",
      ctaDownload: "Download from Releases",
      ctaJournal: "Follow the Dev Journal",
      footerLabel: "Footer links",
      footerText: "Auto Xiao'er — Powered by Auto Xiao'er Studio"
    }
  };

  var textBindings = {
    ".brand span": "brandName",
    "#hero-title": "brandName",
    ".nav-links > a:nth-of-type(1)": "navFeatures",
    ".nav-links > a:nth-of-type(2)": "navHow",
    ".nav-links > a:nth-of-type(3)": "navScenarios",
    ".nav-links > a:nth-of-type(4)": "navShots",
    ".nav-links > a:nth-of-type(5)": "navContact",
    ".nav-links > a:nth-of-type(6)": "navDownload",
    ".hero-tagline": "heroTagline",
    ".hero-cta > a:nth-child(1)": "heroDownload",
    ".hero-cta > a:nth-child(2)": "heroSource",
    ".hero-cta > a:nth-child(3)": "heroVideo",
    ".hero-note": "heroNote",
    "#features-title": "featuresTitle",
    "#features .section-lead": "featuresLead",
    ".feature-card:nth-child(1) h3": "feature1Title",
    ".feature-card:nth-child(1) p": "feature1Text",
    ".feature-card:nth-child(2) h3": "feature2Title",
    ".feature-card:nth-child(2) p": "feature2Text",
    ".feature-card:nth-child(3) h3": "feature3Title",
    ".feature-card:nth-child(3) p": "feature3Text",
    ".feature-card:nth-child(4) h3": "feature4Title",
    ".feature-card:nth-child(4) p": "feature4Text",
    ".feature-card:nth-child(5) h3": "feature5Title",
    ".feature-card:nth-child(5) p": "feature5Text",
    ".feature-card:nth-child(6) h3": "feature6Title",
    ".feature-card:nth-child(6) p": "feature6Text",
    "#how-title": "howTitle",
    "#how-title + .section-lead": "howLead",
    ".step-card:nth-child(1) h3": "step1Title",
    ".step-card:nth-child(1) p": "step1Text",
    ".step-card:nth-child(2) h3": "step2Title",
    ".step-card:nth-child(2) p": "step2Text",
    ".step-card:nth-child(3) h3": "step3Title",
    ".step-card:nth-child(3) p": "step3Text",
    "#how .section-lead a": "guideLink",
    "#scenarios-title": "scenariosTitle",
    "#scenarios > .wrap > .section-lead": "scenariosLead",
    ".scenario-card:nth-child(1) .role": "scenario1Role",
    ".scenario-card:nth-child(1) .scenario-chat div:nth-child(2)": "scenario1Prompt",
    ".scenario-card:nth-child(1) h4": "scenario1Title",
    ".scenario-card:nth-child(1) li:nth-child(1)": "scenario1Item1",
    ".scenario-card:nth-child(1) li:nth-child(2)": "scenario1Item2",
    ".scenario-card:nth-child(1) .scenario-caption": "scenario1Caption",
    ".scenario-card:nth-child(2) .role": "scenario2Role",
    ".scenario-card:nth-child(2) .scenario-chat div:nth-child(2)": "scenario2Prompt",
    ".scenario-card:nth-child(2) h4": "scenario2Title",
    ".scenario-card:nth-child(2) li:nth-child(1)": "scenario2Item1",
    ".scenario-card:nth-child(2) li:nth-child(2)": "scenario2Item2",
    ".scenario-card:nth-child(2) .scenario-caption": "scenario2Caption",
    ".scenario-card:nth-child(3) .role": "scenario3Role",
    ".scenario-card:nth-child(3) .scenario-chat div:nth-child(2)": "scenario3Prompt",
    ".scenario-card:nth-child(3) h4": "scenario3Title",
    ".scenario-card:nth-child(3) li:nth-child(1)": "scenario3Item1",
    ".scenario-card:nth-child(3) li:nth-child(2)": "scenario3Item2",
    ".scenario-card:nth-child(3) .scenario-caption": "scenario3Caption",
    ".scenario-card:nth-child(4) .role": "scenario4Role",
    ".scenario-card:nth-child(4) .scenario-chat div:nth-child(2)": "scenario4Prompt",
    ".scenario-card:nth-child(4) h4": "scenario4Title",
    ".scenario-card:nth-child(4) li:nth-child(1)": "scenario4Item1",
    ".scenario-card:nth-child(4) li:nth-child(2)": "scenario4Item2",
    ".scenario-card:nth-child(4) .scenario-caption": "scenario4Caption",
    "#shots-title": "shotsTitle",
    "#shots .section-lead": "shotsLead",
    ".shot-carousel-hint": "carouselHint",
    "#contact-title": "contactTitle",
    "#contact > .wrap > .section-lead": "contactLead",
    ".contact-card:nth-child(1) h3": "contact1Title",
    ".contact-card:nth-child(1) p": "contact1Text",
    ".contact-card:nth-child(2) h3": "contact2Title",
    ".contact-card:nth-child(2) p": "contact2Text",
    "#cta-title": "ctaTitle",
    ".final-cta > p": "ctaText",
    ".final-buttons > a:nth-child(1)": "ctaDownload",
    ".final-buttons > a:nth-child(2)": "ctaJournal",
    ".site-footer > p": "footerText"
  };

  // Replace the English URLs here when localized screenshots are ready.
  var localizedImages = [
    {
      selector: ".hero-phone img",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_1_cut.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_1_cut.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(1) img",
      linkSelector: ".shot-carousel-slide:nth-child(1) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_1.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_1.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(2) img",
      linkSelector: ".shot-carousel-slide:nth-child(2) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_2.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_2.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(3) img",
      linkSelector: ".shot-carousel-slide:nth-child(3) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_3.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_3.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(4) img",
      linkSelector: ".shot-carousel-slide:nth-child(4) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_4.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_4.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(5) img",
      linkSelector: ".shot-carousel-slide:nth-child(5) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_5.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_5.jpg"
    },
    {
      selector: ".shot-carousel-slide:nth-child(6) img",
      linkSelector: ".shot-carousel-slide:nth-child(6) a",
      "zh-CN": "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_6.jpg",
      en: "https://raw.githubusercontent.com/Joy-word/AutoXiaoer/main/screenshots/screenshot_6.jpg"
    }
  ];

  function getStoredLanguage() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch (_) {
      return null;
    }
  }

  function storeLanguage(language) {
    try {
      localStorage.setItem(STORAGE_KEY, language);
    } catch (_) {
      // Language switching still works when storage is unavailable.
    }
  }

  function getInitialLanguage() {
    var stored = getStoredLanguage();
    if (SUPPORTED_LANGUAGES.indexOf(stored) !== -1) return stored;
    return navigator.language && navigator.language.toLowerCase().indexOf("zh") === 0
      ? "zh-CN"
      : "en";
  }

  function format(message, values) {
    return Object.keys(values || {}).reduce(function (result, key) {
      return result.replace("{" + key + "}", values[key]);
    }, message);
  }

  function translate(key, values) {
    var language = document.documentElement.lang || DEFAULT_LANGUAGE;
    var dictionary = messages[language] || messages[DEFAULT_LANGUAGE];
    return format(dictionary[key] || messages[DEFAULT_LANGUAGE][key] || key, values);
  }

  function setAttribute(selector, attribute, value) {
    var element = document.querySelector(selector);
    if (element) element.setAttribute(attribute, value);
  }

  function applyLanguage(language, persist) {
    if (SUPPORTED_LANGUAGES.indexOf(language) === -1) language = DEFAULT_LANGUAGE;
    var dictionary = messages[language];

    document.documentElement.lang = language;
    document.title = dictionary.title;
    setAttribute('meta[name="description"]', "content", dictionary.description);
    setAttribute('meta[property="og:title"]', "content", dictionary.ogTitle);
    setAttribute('meta[property="og:description"]', "content", dictionary.ogDescription);

    Object.keys(textBindings).forEach(function (selector) {
      var element = document.querySelector(selector);
      if (element) element.textContent = dictionary[textBindings[selector]];
    });

    setAttribute(".nav-links", "aria-label", dictionary.navLabel);
    setAttribute(".language-switcher", "aria-label", dictionary.languageLabel);
    setAttribute("[data-shot-carousel]", "aria-roledescription", dictionary.carouselRole);
    setAttribute("[data-shot-carousel]", "aria-label", dictionary.carouselLabel);
    setAttribute(".shot-carousel-prev", "aria-label", dictionary.carouselPrev);
    setAttribute(".shot-carousel-next", "aria-label", dictionary.carouselNext);
    setAttribute(".shot-carousel-dots", "aria-label", dictionary.carouselDots);
    setAttribute(".site-footer nav", "aria-label", dictionary.footerLabel);
    setAttribute(".hero-phone img", "alt", dictionary.heroImageAlt);
    setAttribute(".contact-card:nth-child(1) img", "alt", dictionary.contact1Alt);
    setAttribute(".contact-card:nth-child(2) img", "alt", dictionary.contact2Alt);

    document.querySelectorAll("[data-contact-language]").forEach(function (element) {
      element.hidden = element.getAttribute("data-contact-language") !== language;
    });

    document.querySelectorAll(".shot-carousel-slide img").forEach(function (image, index) {
      image.alt = format(dictionary.screenshotAlt, { number: index + 1 });
    });

    localizedImages.forEach(function (source) {
      var image = document.querySelector(source.selector);
      if (image) image.src = source[language] || source[DEFAULT_LANGUAGE];
      if (source.linkSelector) {
        var link = document.querySelector(source.linkSelector);
        if (link) link.href = source[language] || source[DEFAULT_LANGUAGE];
      }
    });

    var guideLink = document.querySelector("#how .section-lead a");
    if (guideLink) {
      guideLink.href = language === "en"
        ? "https://github.com/Joy-word/AutoXiaoer/blob/main/README_en.md"
        : "https://github.com/Joy-word/AutoXiaoer/blob/main/README.md";
    }

    document.querySelectorAll("[data-language]").forEach(function (button) {
      var active = button.getAttribute("data-language") === language;
      button.setAttribute("aria-pressed", active ? "true" : "false");
    });

    if (persist) storeLanguage(language);
    document.dispatchEvent(new CustomEvent("languagechange", { detail: { language: language } }));
  }

  window.siteI18n = {
    applyLanguage: applyLanguage,
    t: translate
  };

  document.querySelectorAll("[data-language]").forEach(function (button) {
    button.addEventListener("click", function () {
      applyLanguage(button.getAttribute("data-language"), true);
    });
  });

  applyLanguage(getInitialLanguage(), false);
})();
