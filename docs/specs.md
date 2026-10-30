# J3R452SID (JCOP 4.5 P71D600) ICカード 

J3R452 white cards, initialize, unfused, T=1, defaulted ATR, SCP03.

Java Card 3.0.5 Classic.

ISO/IEC 14443 Type A, ISO/IEC 7816に対応。
(Type Bに対応しているという話もあるが不明)

## 規格

- P71D600
- Use CMOS 40nm process
- Use 16bit SmartMX3 processor
- Provide 450KB Flash storage space FLASH
- Provide 12.3KB RAM memory space
- Provide DES/AES symmetric co-processor
- Provide Fame3/RSA/ECC asymmetric co-processor
- Support ISO14443 contactless interface
- Support ISO7816 contact interface
- JCOP 4.5 (JavaCard 3.0.5 Classic)
- Passed Common Criteria EAL 6+ certification
- Passed EMVCo Platform certification
- Passed FIPS 140-3 Level 3 certification
- Compliant with Java Card v3.0.5 Classic standard
- Compliant with GlobalPlatform v2.3 standard
- Support T=1/T=CL
- Support SHA1/SHA hash algorithms
- Support DES symmetric algorithm: 56/112/168 bits
- Support AES symmetric algorithm: 128/192/256 bits
- Support RSA asymmetric algorithm: 512~4096 bits
- Support ECC asymmetric algorithm: 128~521 bits
- Secure ID (SID) Series
- Provide On Card Key Generation feature
- no Mifare applet (if need , will be customized order)

- JCOP Extended API – R1.3.4 is required.
  - Support EdDSA asymmetric algorithm: Ed25519 (RFC 8032)
  - Support Diffie-Hellman(DH) key exchange algorithm: Curve25519/Curve448 (RFC 7748)
  - 必要ファイルは `extended/` に置く (一般公開されていない NXP 資料のため git には含めない)

