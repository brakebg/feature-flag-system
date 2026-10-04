/** Spec 9.6: the app version shown in the footer (set at build time from the VERSION file). */
export const APP_VERSION: string = import.meta.env.VITE_APP_VERSION || '1.0.0';
