import React from 'react';
import {Composition, continueRender, delayRender, staticFile} from 'remotion';
import {FPS, H, TOTAL, W} from './lib';
import {Main} from './Main';

const FONTS: [string, string, string][] = [
  ['Barlow', 'fonts/BarlowSemiCondensed-Medium.ttf', '500'],
  ['Barlow', 'fonts/BarlowSemiCondensed-SemiBold.ttf', '600'],
  ['Barlow', 'fonts/BarlowSemiCondensed-Bold.ttf', '700'],
  ['Orbitron', 'fonts/Orbitron.ttf', '400 900'],
  ['ShareTech', 'fonts/ShareTechMono-Regular.ttf', '400'],
];

if (typeof document !== 'undefined') {
  const handle = delayRender('Loading fonts');
  Promise.all(
    FONTS.map(([family, url, weight]) =>
      new FontFace(family, `url(${staticFile(url)})`, {weight}).load().then((face) => {
        (document.fonts as unknown as {add: (f: FontFace) => void}).add(face);
      }),
    ),
  )
    .then(() => continueRender(handle))
    .catch((err) => {
      console.error(err);
      continueRender(handle);
    });
}

export const RemotionRoot: React.FC = () => (
  <Composition id="Ventic" component={Main} durationInFrames={TOTAL} fps={FPS} width={W} height={H} />
);
