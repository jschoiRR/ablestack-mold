// Keep legacy API clients compatible while sending one canonical KVM TPM device.
export function deploymentTpmParams (hypervisor, values) {
  if (hypervisor !== 'KVM' || !values.tpmversion || values.tpmversion === 'INHERIT') return {}
  if (values.tpmversion === 'NONE') return { tpmversion: 'NONE' }
  return {
    tpmversion: values.tpmversion,
    'details[0].virtual.tpm.model': values.tpmversion === 'V1_2' ? 'tpm-tis' : values.tpmmodel || 'tpm-tis',
    'details[0].virtual.tpm.version': values.tpmversion === 'V1_2' ? '1.2' : '2.0'
  }
}
