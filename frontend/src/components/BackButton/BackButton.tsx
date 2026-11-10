import React from 'react';
import { Button, CSSObject, MantineTheme } from '@mantine/core';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'tabler-icons-react';

interface Props {
  children: React.ReactNode;
  to?: string;
  href?: string;
  state?: unknown;
  onClick?: React.MouseEventHandler<HTMLAnchorElement>;
  onBack?: () => void;
}

/** Explicit destinations preserve existing list filters and direct-URL fallbacks. */
export default function BackButton({ children, to, href, state, onClick, onBack }: Props) {
  const appearance = {
    variant: 'default' as const,
    radius: 'md' as const,
    leftIcon: <ArrowLeft size={18} aria-hidden="true"/>,
    sx: (theme: MantineTheme): CSSObject => ({
      alignSelf: 'flex-start', width: 'fit-content', maxWidth: '100%', height: 'auto', minHeight: 38,
      padding: '8px 14px', fontSize: 14, fontWeight: 600,
      color: theme.colorScheme === 'dark' ? theme.colors.blue[2] : theme.colors.blue[7],
      borderColor: theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.blue[1],
      backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.white,
      boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
      transition: 'background-color 150ms ease, border-color 150ms ease',
      '&:hover': {
        backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[5] : theme.colors.blue[0],
        borderColor: theme.colors.blue[theme.colorScheme === 'dark' ? 7 : 3],
      },
      '& .mantine-Button-label': { whiteSpace: 'normal', textAlign: 'left', lineHeight: 1.4 },
      '& .mantine-Button-leftIcon': { flexShrink: 0 },
    }),
  };
  if (to) return <Button {...appearance} component={Link} to={to} state={state} onClick={onClick}>{children}</Button>;
  if (href) return <Button {...appearance} component="a" href={href} onClick={onClick}>{children}</Button>;
  return <Button {...appearance} type="button" onClick={onBack}>{children}</Button>;
}
