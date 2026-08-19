(function () {
  var root = document.querySelector("[data-shot-carousel]");
  if (!root) return;

  var viewport = root.querySelector(".shot-carousel-viewport");
  var track = root.querySelector(".shot-carousel-track");
  var slides = Array.prototype.slice.call(
    root.querySelectorAll(".shot-carousel-slide")
  );
  var prevBtn = root.querySelector(".shot-carousel-prev");
  var nextBtn = root.querySelector(".shot-carousel-next");
  var dotsWrap = root.querySelector(".shot-carousel-dots");
  var live = root.querySelector("[data-carousel-live]");

  var index = 0;
  var touchStartX = null;

  function text(key, values) {
    if (window.siteI18n) return window.siteI18n.t(key, values);
    if (key === "carouselPosition") {
      return "第 " + values.current + " 张，共 " + values.total + " 张";
    }
    return "截图区域，按左右方向键切换";
  }

  if (
    !viewport ||
    !track ||
    !slides.length ||
    !prevBtn ||
    !nextBtn ||
    !dotsWrap
  ) {
    return;
  }

  track.id = "shot-carousel-track";

  slides.forEach(function (_, i) {
    var dot = document.createElement("button");
    dot.type = "button";
    dot.className = "shot-carousel-dot";
    dot.setAttribute(
      "aria-label",
      text("carouselPosition", { current: i + 1, total: slides.length })
    );
    dot.addEventListener("click", function () {
      go(i);
    });
    dotsWrap.appendChild(dot);
  });

  var dots = Array.prototype.slice.call(
    dotsWrap.querySelectorAll(".shot-carousel-dot")
  );

  function announce() {
    if (!live) return;
    live.textContent = text("carouselPosition", {
      current: index + 1,
      total: slides.length
    });
  }

  function syncDots() {
    dots.forEach(function (d, i) {
      var active = i === index;
      d.setAttribute(
        "aria-label",
        text("carouselPosition", { current: i + 1, total: slides.length })
      );
      d.setAttribute("aria-current", active ? "true" : "false");
      d.tabIndex = active ? 0 : -1;
    });
    slides.forEach(function (slide, i) {
      slide.setAttribute(
        "aria-label",
        i + 1 + " / " + slides.length
      );
      var hidden = i !== index;
      slide.setAttribute("aria-hidden", hidden ? "true" : "false");
      var link = slide.querySelector("a");
      if (link) {
        if (hidden) link.setAttribute("tabindex", "-1");
        else link.removeAttribute("tabindex");
      }
    });
    announce();
  }

  function go(nextIndex) {
    var n = slides.length;
    index = ((nextIndex % n) + n) % n;
    track.style.transform = "translateX(-" + index * 100 + "%)";
    syncDots();
  }

  prevBtn.addEventListener("click", function () {
    go(index - 1);
  });
  nextBtn.addEventListener("click", function () {
    go(index + 1);
  });

  viewport.addEventListener(
    "keydown",
    function (e) {
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        go(index - 1);
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        go(index + 1);
      }
    },
    true
  );

  viewport.addEventListener(
    "touchstart",
    function (e) {
      touchStartX = e.changedTouches[0].clientX;
    },
    { passive: true }
  );

  viewport.addEventListener(
    "touchend",
    function (e) {
      if (touchStartX === null) return;
      var dx = e.changedTouches[0].clientX - touchStartX;
      touchStartX = null;
      if (Math.abs(dx) < 48) return;
      if (dx > 0) go(index - 1);
      else go(index + 1);
    },
    { passive: true }
  );

  viewport.tabIndex = 0;
  viewport.setAttribute(
    "aria-label",
    text("carouselViewport")
  );

  document.addEventListener("languagechange", function () {
    viewport.setAttribute("aria-label", text("carouselViewport"));
    syncDots();
  });

  syncDots();
  go(0);
})();
