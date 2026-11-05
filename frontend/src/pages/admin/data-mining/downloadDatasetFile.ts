/** Hand a verified API artifact to the browser; callers own request/session cancellation. */
export function downloadDatasetFile({ blob, filename }: { blob: Blob; filename: string }): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  try {
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
  } finally {
    link.remove();
    // Let the browser consume the click before releasing the object URL.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
