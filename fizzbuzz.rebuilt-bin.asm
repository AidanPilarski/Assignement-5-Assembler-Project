.data
str0: .asciiz "Fizz"
str1: .asciiz "Buzz"

.text
.globl main
main:
    addiu $t1, $zero, 1
    addiu $t2, $zero, 101
L2:
    jal L7
    jal L39
    addi $t1, $t1, 1
    bne $t1, $t2, L2
    jal L43
L7:
    addi $sp, $sp, -8
    sw $ra, 0($sp)
    addiu $at, $zero, 3
    div $t1, $at
    mfhi $t3
    addiu $at, $zero, 5
    div $t1, $at
    mfhi $t4
    bne $t3, $zero, L17
    jal L25
L17:
    bne $t4, $zero, L20
    jal L30
    j L22
L20:
    beq $t3, $zero, L22
    jal L35
L22:
    lw $ra, 0($sp)
    addi $sp, $sp, 8
    jr $ra
L25:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 0
    syscall
    jr $ra
L30:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 5
    syscall
    jr $ra
L35:
    addiu $v0, $zero, 1
    addu $a0, $t1, $zero
    syscall
    jr $ra
L39:
    addiu $v0, $zero, 11
    addiu $a0, $zero, 10
    syscall
    jr $ra
L43:
    addiu $v0, $zero, 10
    syscall
