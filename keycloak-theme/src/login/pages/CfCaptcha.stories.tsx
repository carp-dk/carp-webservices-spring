import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "cf-captcha.ftl" });

const meta = {
  title: "login/cf-captcha.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

// Cloudflare's public always-pass test site key, since the real key only works on its configured domains.
// The page submits once the challenge passes, so this story reloads itself.
export const Default: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        captchaSiteKey: "1x00000000000000000000AA",
        captchaAction: "login",
      }}
    />
  ),
};

// Cloudflare's public always-blocks test site key, to show the page while the challenge is unsolved.
export const WithFailedVerification: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        captchaSiteKey: "2x00000000000000000000AB",
        captchaAction: "login",
        message: {
          type: "error",
          summary:
            "Verification failed. Please complete the challenge and try again.",
        },
      }}
    />
  ),
};
