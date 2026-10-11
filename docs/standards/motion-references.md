# Motion references

A reading list for building Apple-grade motion, and a way of thinking about it. It sits beside [Dynamic UI](dynamic-ui.md)
(the rules) and [Design](design.md) (the visual language). Nothing here is a rule. It is where to look when a rule
needs a good example.

**Where it came from.** McCal's notes of 7 Oct 2026, taken from an answer by an outside assistant and kept as given. The
links had tracking parameters, which are removed below. Star counts are as reported then and not checked. One link in
the original pointed at `robertpenner/motion.dev` for Motion, which looks like a mix-up; Motion's own repository is
`motiondivision/motion`, so that is the one listed. Folio is Kotlin and Jetpack Compose, so these web libraries are
**study material for how motion is built, not dependencies**.

## The idea

Apple's feel comes from **continuity**, not from lots of animation. Objects keep their identity as the interface
changes, easing is chosen and consistent, springs are restrained, and timing is precise. Not every object is animated.
The ones that change are tied to where they came from.

A card on the web, and the same thing as a Folio folder:

```
hover / press      scale 1.00 to 1.015, shadow spreads a little, image scale 1.00 to 1.025
tap                the card becomes the next screen (shared-element transition)
                   it fills the viewport, the image keeps its visual position, the text resolves underneath
```

not:

```
tap -> fade everything out -> load -> fade everything back in
```

The first keeps your eye on one object, so your visual system never loses track of it. That is the satisfying part.

## A small motion language

Easing and timing are chosen once and reused, not picked per element. The web version, as given:

```css
:root {
  --ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-standard: cubic-bezier(0.22, 1, 0.36, 1);

  --motion-fast: 160ms;
  --motion-ui: 280ms;
  --motion-large: 600ms;
}

.interactive {
  transition:
    transform var(--motion-ui) var(--ease-out-expo),
    opacity var(--motion-ui) var(--ease-standard);
}

.interactive:hover { transform: scale(1.015); }

@media (prefers-reduced-motion: reduce) {
  .interactive { transition: none; }
}
```

In Folio the same job is done by `FolioMotion` (`Settle`, `Quick`, `Firm`) and `MotionSpeed.spring`, with Reduce Motion
through `LocalReduceMotion`.

## What to study, in the order suggested

1. **Motion (formerly Framer Motion)**, the best starting point for modern UI motion: springs, layout transitions,
   gestures, scroll animation, shared layouts, enter and exit states. Its layout animations give the "connected to where
   it came from" behavior Apple uses constantly. <https://github.com/motiondivision/motion>, docs at <https://motion.dev>
2. **CSS View Transitions**, the web's native answer to "a card, photo or icon becomes the next interface". MDN describes
   them as a way to keep context and reduce the jump between states. Examples:
   <https://github.com/schalkventer/css-view-transition-resources> and
   <https://github.com/thatbeautifuldream/vanilla-view-transitions>. Little code for a lot of polish.
3. **Codrops**, the best single GitHub organization for interaction experiments: scroll text motion, elastic grids,
   scroll-based layout animation, page transitions, hover ideas, and current GSAP and WebGL work. Their recent infinite
   gallery with FLIP transitions, and SVG mask transitions on scroll with GSAP, are the closest to agency or Apple level.
   <https://github.com/codrops>
4. **GSAP**, for the cinematic product-page side: pinned sections, scroll scrubbing, image sequences, text reveals,
   morphs, FLIP and timelines. The full toolset, including the formerly paid plugins, is now free. Look at ScrollTrigger,
   Flip and SplitText first. <https://github.com/greensock/gsap>
5. **Lenis**, lightweight smooth-scroll sync for parallax and WebGL. Native scroll stays the baseline; use it only for an
   intentionally cinematic page, usually with GSAP. <https://github.com/darkroomengineering/lenis>
6. **AutoAnimate**, the opposite end: hand it a container and additions, removals and reordering animate. Motion as a
   default behavior, not an effect. Good for menus, settings panels, filters, notification stacks and expanding content.
   <https://github.com/formkit/auto-animate>
7. **Magic UI**, a cabinet of specimens: blur fades, animated lists, progressive blur, smooth cursors, shimmer, border
   beams. Some is too flashy for Apple's look, but the implementation ideas are good. <https://github.com/magicuidesign/magicui>
8. **React Spring**, for physically based motion: describe the spring, not "300 ms". Drawers, sheets, toggles, elastic
   resizing, things that follow a gesture. Motion covers most of it now, so learn Motion first. <https://github.com/pmndrs/react-spring>
9. **Number Flow**, numbers that roll and move in space instead of being replaced. Battery percentages, counters,
   progress, temperatures, file counts. <https://github.com/barvian/number-flow>
10. **Apple-site clones** (React, Three.js and GSAP iPhone product pages) as dissection specimens, not architecture.
    Many come from the same tutorials. One example: <https://github.com/Krishnendu1910/Apple-Website>

For pure CSS: `transition.css` (46 clip-path and custom-property transitions, <https://github.com/argyleink/transition.css>)
and Codrops' older `PageTransitions` and `HoverEffectIdeas`.

## A second list: micro-interactions, component sets and experiments

Also McCal's notes (7 Oct 2026, from the same outside assistant). The paste kept the names and descriptions but not the
links, so none are listed here; find each by name. I haven't checked any of them. "200+ components" and similar counts
are as reported.

**Component and motion sets**
- **React Bits**, the top pick in the note: 200+ customizable components, including elastic sliders, magnetic
  interactions, animated text, docks, image distortion, liquid backgrounds and circular galleries. Good for experiments.
- **Motion Primitives** (React, Motion, Tailwind): restrained UI animation such as animated dialogs, expandable cards, text
  transitions, tabs and layout continuity. The closest fit to Apple's quiet language.
- **Animate UI** (React, TypeScript, Motion, shadcn): animated everyday components (buttons, dropdowns, accordions, tabs,
  dialogs, icons) inside one consistent system.
- **Theatre.js**: timeline-based choreography for HTML, SVG and Three.js, with a visual editor. For cinematic reveals and
  complex sequences.
- **Aceternity UI**: polished, more spectacle than Apple (chromatic image effects, animated text, canvas effects).
  Valuable for an experimental corner, not as a style to copy.

**CSS-only and technique repos** (these show the method instead of hiding it)
- **AnimXYZ**: composable CSS animations driven by custom properties, no hand-written keyframes.
- **Animate.css**: a large keyframe collection; best for learning timing, entrances, exits and composition.
- **Elastic Grid Scroll**: columns scroll at different speeds for a soft, elastic feel.
- **One Element Scroll**: one element travels across sections and keeps visual continuity (GSAP Flip and ScrollTrigger).
- **Kinetic Type Transitions**: typography takes part in navigation instead of fading away.
- **3D Carousel**: scroll-controlled spatial presentation of content.
(The last three are described as Codrops-style experiments; the paste did not name their owners.)

**The note's own suggestion: an Animation Lab.** Interactive samples where spring stiffness, damping, duration, easing
and amplitude change live, grouped as:

1. **Micro-interactions:** magnetic buttons, spring toggles, elastic sliders, animated icons, haptic-style feedback, number rolling.
2. **Apple-style navigation:** morphing dialogs, expanding cards, dynamic islands, sliding tabs, shared-element transitions, a floating dock.
3. **Editorial and photography:** parallax galleries, image zoom, reveal masks, horizontal scroll, cinematic captions.
4. **Experimental motion:** liquid distortion, blob physics, particle trails, cursor attraction, scroll-velocity effects, 3D tilt.
5. **Advanced UI physics:** draggable stacks, inertial scrolling, rubber-band resistance, spring chaining, gesture velocity.

For a web build it suggests React, TypeScript, Vite, native CSS and Motion, with GSAP for the cinematic pieces; every
sample with a live preview, a source viewer, adjustable physics, a replay control and a reduced-motion comparison. The
point it stresses: a reusable reference library, not a pile of impressive demos that are hard to integrate. Its priorities
were morphing interfaces, elastic controls, magnetic interactions and image transitions.

**Which of this applies to Folio**

| Idea | In Folio |
|---|---|
| Spring playground with live stiffness, damping and duration | The most useful one. A Mockup Lab scene ("Spring lab") with sliders, a replay button, a Reduce Motion comparison, and a readout in Compose terms (`spring(dampingRatio, stiffness)`, plus Apple's response and bounce). Tuning a feel there, then copying the numbers into `FolioMotion`, is cheaper than rebuilding the app per try. Proposed as MO0 in the 0.6.9 plan. |
| Spring toggles and elastic controls | Switch and button press feel. Touch-down scale near 0.96 that returns on a spring (MO8). |
| Sliding tab indicator, "liquid navigation" | The Market tab bar and the segmented controls: the selected pill moves between items instead of jumping. Cheap and visible. |
| Morphing dialogs, expanding cards, shared elements | Icon to folder and icon to menu (MO5); the Market card to its package page. |
| Dynamic-island morphing | The island already exists; its size and content changes are where a spring that carries velocity shows most. |
| Draggable stacks, inertial scrolling, rubber-band, spring chaining, gesture velocity | Smart Stacks, page settle (MO4), edge resistance (MO6). The "advanced UI physics" group is the Home gestures. |
| Number rolling | Battery, unread counts, install progress, the clock. |
| Floating dock | Dock magnification already exists; check that it costs no recomposition per frame. |
| Staggered content | A quiet cascade for a screen's first appearance (Market list, onboarding rows), kept short. |
| Magnetic buttons, cursor attraction, particle trails, liquid distortion, blob physics, 3D image tilt, scroll-velocity effects | Mostly pointer or spectacle effects. Not for a phone launcher's core, apart from a mouse hover on large screens. Page effects (Cube, Carousel, Flipbook) are where Folio already spends its spectacle budget. |
| Editorial and photography (parallax galleries, reveal masks, cinematic captions) | The foliolauncher.com site and the McCal portfolio, not the app. |

## A suggested stack, for a web project

```
CSS transitions and keyframes
        |
CSS View Transitions
        |
Motion
        |
GSAP ScrollTrigger and Flip
        |
Lenis, only when cinematic scrolling needs it
        |
Three.js, only when 3D clearly improves the experience
```

Each layer is added only when the one above it can't do the job.

## What carries over to Folio

| Web idea | Folio, on Android |
|---|---|
| Shared-element transition | An icon grows into its folder or menu from its own bounds (DYN-10). Folio's overlays are `Dialog`s, so Compose's `sharedBounds` doesn't cross them and the bounds are passed by hand. |
| Spring instead of a duration | `FolioMotion` springs through `MotionSpeed.spring`. Compose `spring()` with stiffness and damping ratio. |
| Layout animation (AutoAnimate) | `Modifier.animateItem()` in lazy lists, `animateContentSize()`, `AnimatedContent`. |
| Number Flow | `AnimatedContent` per digit, or a vertical slide on the digit that changed. Candidates: battery, unread counts, install progress, the clock. |
| One easing language | Named springs and durations only. No raw `spring(` outside the tokens (the research found about 40). |
| Hover scale | There is no hover on a phone. The press state does the same job: a touch-down scale near 0.96 that returns on a spring. A mouse or stylus on a large screen can still get the hover. |
| Reduced motion | `LocalReduceMotion`, and Animation Speed (`MotionSpeed`). |
| Smooth scroll sync (Lenis) | Not needed. Android's own fling and the pager's snap are the baseline. |
| The frame budget | 120 Hz means 8.3 ms a frame. Smoothness is frames first, curves second. |

## Seeing it

The Mockup Lab (kept outside git) can slow, pause and record motion: a Speed control in its Motion panel and
`record.mjs`, which writes a flow or screen as MP4, GIF, WebM, WebP or APNG. Use it to compare a candidate curve to the
current one side by side before touching Kotlin.
