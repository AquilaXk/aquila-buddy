#!/usr/bin/env bash
set -euo pipefail

root="infra/terraform/oci/always-free-a1-flex"

fail() {
  printf 'oci always-free baseline check failed: %s\n' "$1" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "missing file: $path"
}

require_contains() {
  local path="$1"
  local expected="$2"
  grep -F -- "$expected" "$path" >/dev/null || fail "$path must contain: $expected"
}

[[ -d "$root" ]] || fail "missing terraform root: $root"

for file in \
  README.md \
  cloud-init.yaml \
  compute.tf \
  images.tf \
  locals.tf \
  network.tf \
  outputs.tf \
  providers.tf \
  storage.tf \
  terraform.env.example \
  terraform.tfvars.example \
  variables.tf \
  versions.tf
do
  require_file "$root/$file"
done

require_contains "$root/versions.tf" 'source  = "oracle/oci"'
require_contains "$root/locals.tf" 'Project      = "aquila-buddy"'
require_contains "$root/variables.tf" 'default     = "aquila-buddy-a1"'
require_contains "$root/variables.tf" 'default     = "aquilabuddy"'
require_contains "$root/variables.tf" 'default     = "aquilabuddyvcn"'
require_contains "$root/variables.tf" 'default     = 4'
require_contains "$root/variables.tf" 'default     = 24'
require_contains "$root/variables.tf" 'default     = 50'
require_contains "$root/variables.tf" 'default     = 150'
require_contains "$root/variables.tf" 'default     = 0'
require_contains "$root/compute.tf" 'shape                = local.instance_shape'
require_contains "$root/compute.tf" 'preserve_boot_volume = false'
require_contains "$root/storage.tf" 'vpus_per_gb         = var.data_volume_vpus_per_gb'
require_contains "$root/cloud-init.yaml" 'docker_data_root="${data_volume_mount_path}/docker"'
require_contains "$root/terraform.tfvars.example" 'name_prefix    = "aquila-buddy-a1"'
require_contains "$root/terraform.tfvars.example" 'hostname_label = "aquilabuddy"'
require_contains "$root/terraform.tfvars.example" 'vcn_dns_label  = "aquilabuddyvcn"'
require_contains "$root/terraform.env.example" 'export TF_VAR_name_prefix=aquila-buddy-a1'
require_contains "$root/terraform.env.example" 'export TF_VAR_hostname_label=aquilabuddy'
require_contains "$root/README.md" 'aquila-buddy'
require_contains ".gitignore" '*.tfvars'
require_contains ".gitignore" '!*.tfvars.example'

if find "$root" \( -name '*.tfstate' -o -name '*.tfstate.*' -o -name '*.tfplan' -o -name 'terraform.tfvars' \) | grep -q .; then
  fail "terraform local state, plan, or real tfvars file must not be committed"
fi

if grep -R --line-number --fixed-strings 'BEGIN PRIVATE KEY' "$root" >/dev/null; then
  fail "private key material must not be committed"
fi

printf 'oci always-free baseline check passed\n'
