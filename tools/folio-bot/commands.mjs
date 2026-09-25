/**
 * The bot's commands: what Discord registers, and what each one answers.
 *
 * Every handler takes the options Discord sent and a `sources` object, and returns a string. Nothing here touches
 * the network or Discord directly, so all of it is testable without either.
 */
import { SITE } from './sources.mjs'

/** Discord's limit on message content. Answers are trimmed to it rather than rejected by the API. */
export const LIMIT = 2000

/** What gets registered. Keep the descriptions plain: they show in the command picker as someone types. */
export const COMMANDS = [
  { name: 'version', description: 'The current Folio release, and where to get it' },
  {
    name: 'changelog',
    description: 'What changed in a release',
    options: [{ name: 'version', description: 'For example 0.6.6. The newest one by default.', type: 3 }],
  },
  {
    name: 'roadmap',
    description: 'What is being built, planned, or only explored',
    options: [
      {
        name: 'when',
        description: 'Next, Later or Exploring. All of them by default.',
        type: 3,
        choices: [
          { name: 'Next', value: 'next' },
          { name: 'Later', value: 'later' },
          { name: 'Exploring', value: 'exploring' },
        ],
      },
    ],
  },
  {
    name: 'help',
    description: 'The help page for something',
    options: [{ name: 'topic', description: 'For example gestures, widgets, backup', type: 3 }],
  },
  {
    name: 'tweak',
    description: 'What a tweak changes',
    options: [{ name: 'name', description: 'For example Cabinet', type: 3 }],
  },
  {
    name: 'screens',
    description: 'Whether Folio fits a screen that size',
    options: [{ name: 'width', description: 'Width in dp, for example 932', type: 4, required: true }],
  },
]

const trim = (text, limit = LIMIT) =>
  text.length <= limit ? text : `${text.slice(0, limit - 2).trimEnd()}…`

/**
 * A bullet cut to its first sentence, keeping the bold lead. A changelog bullet is written lead first, so the first
 * sentence is the part meant to be read on its own; cutting mid-word instead reads like a mistake.
 *
 * Under 40 characters there is no sentence worth trusting: a full stop that early is usually a version number.
 */
export function firstSentence(text, limit = 190) {
  const oneLine = text.replace(/\s+/g, ' ').trim()
  if (oneLine.length <= limit) return oneLine
  const sentence = /^(.{40,}?[.!?])\s/.exec(oneLine)
  if (sentence && sentence[1].length <= limit) return sentence[1]
  // One long sentence: end on a clause if there is one late enough to keep the point, and on a word regardless.
  const cut = oneLine.slice(0, limit - 1)
  const clause = Math.max(cut.lastIndexOf(', '), cut.lastIndexOf('; '))
  const word = cut.lastIndexOf(' ')
  const at = clause > limit * 0.6 ? clause : word > 0 ? word : cut.length
  return `${cut.slice(0, at).trimEnd()}…`
}

function leads(body, count) {
  return body
    .split('\n')
    .filter((line) => line.startsWith('- '))
    .slice(0, count)
    .map((line) => `- ${firstSentence(line.slice(2))}`)
}

export const handlers = {
  async version(_options, sources) {
    const release = await sources.release()
    return [
      `**Folio ${release.version}**, released ${release.published}.`,
      `${release.size ? `${release.size} · ` : ''}Android 12 and up · free and open source, no ads`,
      `[Download](${release.apk}) · [How to install it](${SITE}/download/) · [What changed](${SITE}/changelog/${release.version}/)`,
    ].join('\n')
  },

  async changelog(options, sources) {
    const entries = await sources.changelog()
    const released = entries.filter((entry) => entry.date)
    const wanted = options.version
      ? released.find((entry) => entry.version === options.version.replace(/^v/, ''))
      : released[0]
    if (!wanted) {
      const known = released.slice(0, 8).map((entry) => entry.version).join(', ')
      return `No release called ${options.version}. The recent ones are ${known}.`
    }
    const lines = leads(wanted.body, 5)
    return trim([
      `**Folio ${wanted.version}**, released ${wanted.date}.`,
      ...lines,
      `\n[All of it](${SITE}/changelog/${wanted.version}/)`,
    ].join('\n'))
  },

  async roadmap(options, sources) {
    const sections = (await sources.roadmap()).filter((section) => !section.shipped)
    const wanted = options.when
      ? sections.filter((section) => section.name.toLowerCase() === options.when)
      : sections
    if (!wanted.length) return `Nothing under ${options.when}. Try next, later or exploring.`
    const blocks = wanted.map((section) => {
      const items = section.items.slice(0, 6).map((item) => `- **${item.title}:** ${item.detail}`)
      return [`**${section.name}**`, ...items].join('\n')
    })
    return trim([...blocks, `\n[The whole roadmap](${SITE}/roadmap/)`].join('\n\n'))
  },

  async help(options, sources) {
    const pages = await sources.help()
    if (!pages.length) return `The help pages are at ${SITE}/help/`
    if (!options.topic) {
      return [`**Folio's help pages**`, ...pages.map((page) => `- [${page.title}](${page.url})`)].join('\n')
    }
    const needle = options.topic.toLowerCase()
    const match =
      pages.find((page) => page.slug === needle) ??
      pages.find((page) => page.slug.includes(needle) || page.title.toLowerCase().includes(needle))
    if (!match) {
      return [
        `Nothing about "${options.topic}". These are the pages:`,
        ...pages.map((page) => `- [${page.title}](${page.url})`),
      ].join('\n')
    }
    return `**${match.title}**\n${match.url}`
  },

  async tweak(options, sources) {
    const tweaks = await sources.tweaks()
    if (!options.name) {
      return [
        `**Folio's tweaks**`,
        ...tweaks.map((tweak) => `- **${tweak.name}:** ${tweak.description}`),
        `\n[What each one changes](${SITE}/tweaks/)`,
      ].join('\n')
    }
    const needle = options.name.toLowerCase()
    const match = tweaks.find((tweak) => tweak.name.toLowerCase() === needle) ??
      tweaks.find((tweak) => tweak.name.toLowerCase().includes(needle) || tweak.id.includes(needle))
    if (!match) {
      return `No tweak called ${options.name}. There is ${tweaks.map((tweak) => tweak.name).join(', ')}.`
    }
    const screens = match.screens.length ? `\nScreens: ${match.screens.join(', ')}.` : ''
    return trim(`**${match.name}** ${match.version}\n${match.description}${screens}\n\n${SITE}/tweaks/`)
  },

  async screens(options, sources) {
    const width = Number(options.width)
    if (!Number.isFinite(width) || width <= 0) return 'Give me a width in dp, for example 932.'
    const devices = await sources.screens()
    // The same rule the site's screens page uses: a second pane needs 600dp, a third needs 1200.
    const panes = width >= 1200 ? 'three panes' : width >= 600 ? 'two panes' : 'one pane'
    // The narrowest screen the tests run against, read rather than written down, so it moves when the matrix does.
    const narrowest = Math.min(...devices.map((device) => device.width))
    const supported = width >= narrowest
    const nearest = devices
      .map((device) => ({ ...device, gap: Math.abs(device.width - width) }))
      .sort((a, b) => a.gap - b.gap)
      .slice(0, 3)
    return trim([
      supported
        ? `**${width}dp**: yes, and Folio draws **${panes}** there.`
        : `**${width}dp** is narrower than anything Folio is tested on, which starts at ${narrowest}dp.`,
      `Closest screens in the test matrix: ${nearest.map((d) => `${d.name} (${d.width}×${d.height})`).join(', ')}.`,
      `\n[Every screen it is tested on](${SITE}/screens/)`,
    ].join('\n'))
  },
}

/** Turns Discord's option array into a plain object, which is what the handlers want. */
export function optionsOf(interaction) {
  return Object.fromEntries((interaction?.data?.options ?? []).map((option) => [option.name, option.value]))
}

export async function run(name, options, sources) {
  const handler = handlers[name]
  if (!handler) return `I do not know ${name}.`
  try {
    return await handler(options, sources)
  } catch (error) {
    // The person asked a reasonable question; a stack trace is not an answer. The log keeps the detail.
    console.error(`${name} failed: ${error.message}`)
    return 'That did not work just now. GitHub or the site may be having a moment; try again shortly.'
  }
}
