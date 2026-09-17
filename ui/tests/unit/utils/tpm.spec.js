import { deploymentTpmParams } from '@/utils/tpm'

describe('TPM deployment request', () => {
  it('does not send an implicit NONE over an inherited template setting', () => {
    expect(deploymentTpmParams('KVM', { tpmversion: 'INHERIT' })).toEqual({})
    expect(deploymentTpmParams('KVM', {})).toEqual({})
  })
  it('keeps explicit disabled distinct from inheritance', () => {
    expect(deploymentTpmParams('KVM', { tpmversion: 'NONE', tpmmodel: 'tpm-crb' })).toEqual({ tpmversion: 'NONE' })
  })
  it('sends matching legacy and canonical version and model', () => {
    expect(deploymentTpmParams('KVM', { tpmversion: 'V2_0', tpmmodel: 'tpm-crb' })).toEqual({
      tpmversion: 'V2_0',
      'details[0].virtual.tpm.model': 'tpm-crb',
      'details[0].virtual.tpm.version': '2.0'
    })
  })
  it('uses TIS for legacy callers without a model', () => {
    expect(deploymentTpmParams('KVM', { tpmversion: 'V2_0' })['details[0].virtual.tpm.model']).toBe('tpm-tis')
  })
  it('uses only TIS for version 1.2, even after a previous CRB selection', () => {
    expect(deploymentTpmParams('KVM', { tpmversion: 'V1_2', tpmmodel: 'tpm-crb' })).toEqual({
      tpmversion: 'V1_2',
      'details[0].virtual.tpm.model': 'tpm-tis',
      'details[0].virtual.tpm.version': '1.2'
    })
  })
  it('leaves other hypervisor TPM conventions untouched', () => {
    expect(deploymentTpmParams('VMware', { tpmversion: 'V2_0' })).toEqual({})
  })
})
