import type { GlobalThemeOverrides } from 'naive-ui'

/** 品牌色（AI-Native UI：紫 + 青） */
export const brand = {
  primary: '#7C3AED',
  primaryHover: '#8B5CF6',
  primaryPressed: '#6D28D9',
  primarySuppl: '#A78BFA',
  info: '#0891B2',
  infoHover: '#06B6D4',
  infoPressed: '#0E7490',
  success: '#10B981',
  successHover: '#34D399',
  successPressed: '#059669',
  warning: '#F59E0B',
  warningHover: '#FBBF24',
  warningPressed: '#D97706',
  error: '#EF4444',
  errorHover: '#F87171',
  errorPressed: '#DC2626'
}

const baseCommon = {
  primaryColor: brand.primary,
  primaryColorHover: brand.primaryHover,
  primaryColorPressed: brand.primaryPressed,
  primaryColorSuppl: brand.primarySuppl,
  infoColor: brand.info,
  infoColorHover: brand.infoHover,
  infoColorPressed: brand.infoPressed,
  successColor: brand.success,
  successColorHover: brand.successHover,
  successColorPressed: brand.successPressed,
  warningColor: brand.warning,
  warningColorHover: brand.warningHover,
  warningColorPressed: brand.warningPressed,
  errorColor: brand.error,
  errorColorHover: brand.errorHover,
  errorColorPressed: brand.errorPressed,
  borderRadius: '8px',
  borderRadiusSmall: '6px',
  fontFamily:
    "'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif"
}

export const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...baseCommon,
    bodyColor: '#F7F7FB',
    cardColor: '#FFFFFF',
    modalColor: '#FFFFFF',
    popoverColor: '#FFFFFF',
    inputColor: '#FFFFFF',
    tableColor: '#FFFFFF',
    tableHeaderColor: '#F4F4F8',
    hoverColor: '#F4F4F8',
    actionColor: '#F4F4F8',
    dividerColor: '#ECEEF9',
    borderColor: '#E4E7F0'
  },
  Card: { borderRadius: '10px' },
  Button: { borderRadiusMedium: '8px' },
  Layout: {
    siderColor: '#FFFFFF',
    siderBorderColor: '#ECEEF9'
  },
  Menu: {
    itemTextColor: '#475569',
    itemIconColor: '#64748B',
    itemTextColorHover: '#7C3AED',
    itemIconColorHover: '#7C3AED',
    itemTextColorActive: '#7C3AED',
    itemIconColorActive: '#7C3AED',
    itemColorActive: '#F5F3FF'
  },
  DataTable: {
    thColor: '#F4F4F8',
    borderColor: '#EEF0F6'
  }
}

export const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...baseCommon,
    bodyColor: '#0F1115',
    cardColor: '#15171C',
    modalColor: '#1A1D23',
    popoverColor: '#1A1D23',
    inputColor: '#181B21',
    tableColor: '#15171C',
    tableHeaderColor: '#1A1D23',
    hoverColor: '#1E2229',
    actionColor: '#1E2229',
    dividerColor: 'rgba(255,255,255,0.08)',
    borderColor: 'rgba(255,255,255,0.10)'
  },
  Card: { borderRadius: '10px' },
  Button: { borderRadiusMedium: '8px' },
  Layout: {
    siderColor: '#15171C',
    siderBorderColor: 'rgba(255,255,255,0.08)'
  },
  Menu: {
    itemTextColor: '#A6ADBB',
    itemIconColor: '#8B949E',
    itemTextColorHover: '#D8B4FE',
    itemIconColorHover: '#D8B4FE',
    itemTextColorActive: '#D8B4FE',
    itemIconColorActive: '#D8B4FE',
    itemColorActive: 'rgba(124,58,237,0.16)'
  },
  DataTable: {
    thColor: '#1A1D23',
    borderColor: 'rgba(255,255,255,0.08)'
  }
}
