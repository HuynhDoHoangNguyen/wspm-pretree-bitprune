import React from 'react';
import { useMantineTheme } from '@mantine/core';
import { BRAND_DESCRIPTION, BRAND_NAME } from 'constants/Brand';

interface ElectroLogoProps {
  width?: number;
}

// Keep the existing component name and width API for all current header/login consumers.
function ElectroLogo({ width = 120 }: ElectroLogoProps) {
  const theme = useMantineTheme();
  return <svg viewBox="0 0 176 43" width={width} role="img" aria-label={BRAND_NAME}>
    <desc>{BRAND_DESCRIPTION}</desc>
    <text x={0} y={32} fontFamily="Arial, sans-serif" fontSize={30} fontWeight={800}
      textLength={174} lengthAdjust="spacingAndGlyphs" fill={theme.colors.blue[6]}>{BRAND_NAME}</text>
  </svg>;
}

export default ElectroLogo;
