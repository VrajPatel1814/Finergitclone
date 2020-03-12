package finergit.ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import finergit.FinerGitConfig;
import finergit.ast.token.*;

public class JavaFileVisitor extends ASTVisitor {

  private static final Logger log = LoggerFactory.getLogger(JavaFileVisitor.class);

  public final Path path;
  private final FinerGitConfig config;
  private final Stack<FinerJavaModule> moduleStack;
  private final List<FinerJavaModule> moduleList;
  private final Stack<Class<?>> contexts;
  private int classNestLevel;

  public JavaFileVisitor(final Path path, final FinerGitConfig config) {

    this.path = path;
    this.config = config;
    this.moduleStack = new Stack<>();
    this.moduleList = new ArrayList<>();
    this.contexts = new Stack<>();
    this.classNestLevel = 0;

    final Path parent = path.getParent();
    final String fileName = FilenameUtils.getBaseName(path.toString());
    final FinerJavaFile finerJavaFile = new FinerJavaFile(parent, fileName, config);
    this.moduleStack.push(finerJavaFile);
    this.moduleList.add(finerJavaFile);
  }

  public List<FinerJavaModule> getFinerJavaModules() {
    return this.moduleList.stream()
        .filter(m -> (FinerJavaFile.class == m.getClass() && config.isPeripheralFileGenerated())
            || (FinerJavaClass.class == m.getClass() && config.isClassFileGenerated())
            || (FinerJavaMethod.class == m.getClass() && config.isMethodFileGenerated())
            || (FinerJavaField.class == m.getClass() && config.isFieldFileGenerated()))
        .collect(Collectors.toList());
  }

  @Override
  public boolean visit(final AnnotationTypeDeclaration node) {

    this.classNestLevel++;

    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    this.contexts.push(CLASSNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert CLASSNAME.class == context : "error happened at JavaFileVisitor#visit(AnnotationTypeDeclaration)";

    this.addToPeekModule(new LEFTANNOTATIONBRACKET());

    // ボディの処理
    for (final Object o : node.bodyDeclarations()) {
      final BodyDeclaration body = (BodyDeclaration) o;
      body.accept(this);
    }

    this.addToPeekModule(new RIGHTANNOTATIONBRACKET());

    this.classNestLevel--;

    return false;
  }

  @Override
  public boolean visit(final AnnotationTypeMemberDeclaration node) {

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    node.getType()
        .accept(this);

    this.contexts.push(VARIABLENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert VARIABLENAME.class == context : "error happened at JavaFileVisitor#visit(AnnotationTypeMemberDeclaration)";

    final Expression defaultValue = node.getDefault();
    if (null != defaultValue) {
      this.addToPeekModule(new ASSIGN());
      defaultValue.accept(this);
    }

    this.addToPeekModule(new ANNOTATIONTYPEMEMBERDECLARATIONSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final AnonymousClassDeclaration node) {

    this.classNestLevel++;

    this.addToPeekModule(new LEFTANONYMOUSCLASSBRACKET());

    for (final Object o : node.bodyDeclarations()) {
      final BodyDeclaration body = (BodyDeclaration) o;
      body.accept(this);
    }

    this.addToPeekModule(new RIGHTANONYMOUSCLASSBRACKET());

    this.classNestLevel--;

    return false;
  }

  @Override
  public boolean visit(final ArrayAccess node) {

    node.getArray()
        .accept(this);

    this.addToPeekModule(new LEFTSQUAREBRACKET());

    node.getIndex()
        .accept(this);

    this.addToPeekModule(new RIGHTSQUAREBRACKET());

    return false;
  }

  @Override
  public boolean visit(final ArrayCreation node) {

    this.addToPeekModule(new NEW());

    node.getType()
        .accept(this);

    final ArrayInitializer initializer = node.getInitializer();
    if (null != initializer) {
      initializer.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final ArrayInitializer node) {

    this.addToPeekModule(new LEFTARRAYINITIALIZERBRACKET());

    final List<?> expressions = node.expressions();
    if (null != expressions && !expressions.isEmpty()) {
      ((Expression) expressions.get(0)).accept(this);
      for (int index = 1; index < expressions.size(); index++) {
        this.addToPeekModule(new ARRAYINITIALIZERCOMMA());
        ((Expression) expressions.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTARRAYINITIALIZERBRACKET());

    return false;
  }

  // 変更の必要なし
  @Override
  public boolean visit(final ArrayType node) {
    return super.visit(node);
  }

  @Override
  public boolean visit(final AssertStatement node) {

    this.addToPeekModule(new ASSERT());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new COLON());

    final Expression message = node.getMessage();
    if (null != message) {
      message.accept(this);
    }

    this.addToPeekModule(new ASSERTSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final Assignment node) {

    node.getLeftHandSide()
        .accept(this);

    this.addToPeekModule(new ASSIGN());

    node.getRightHandSide()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final Block node) {

    final ASTNode parent = node.getParent();
    this.addBracket(parent, true);

    for (final Object o : node.statements()) {
      final Statement statement = (Statement) o;
      statement.accept(this);
    }

    this.addBracket(parent, false);

    return false;
  }

  /**
   * ブラケット"{"もしくは"}"を追加するためのメソッド．第一引数はコンテキスト情報（親ノード情報）．第二引数は"{"か"}"の選択のためのboolean型．
   * 
   * @param parent
   * @param left
   */
  private void addBracket(final ASTNode parent, final boolean left) {
    if (TypeDeclaration.class == parent.getClass()) {
      // class宣言のときにはここには来ないはず
      log.error("unexpected state at type declaration.");
    } else if (AnonymousClassDeclaration.class == parent.getClass()) {
      // 匿名class宣言のときにはここには来ないはず
      log.error("unexpected state at anonymous type declaration.");
    } else if (MethodDeclaration.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTMETHODBRACKET() : new RIGHTMETHODBRACKET());
    } else if (Initializer.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTINITIALIZERBRACKET() : new RIGHTINITIALIZERBRACKET());
    } else if (DoStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTDOBRACKET() : new RIGHTDOBRACKET());
    } else if (ForStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTFORBRACKET() : new RIGHTFORBRACKET());
    } else if (EnhancedForStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTENHANCEDFORBRACKET() : new RIGHTENHANCEDFORBRACKET());
    } else if (IfStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTIFBRACKET() : new RIGHTIFBRACKET());
    } else if (LambdaExpression.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTLAMBDABRACKET() : new RIGHTLAMBDABRACKET());
    } else if (Block.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTSIMPLEBLOCKBRACKET() : new RIGHTSIMPLEBLOCKBRACKET());
    } else if (SynchronizedStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTSYNCHRONIZEDBRACKET() : new RIGHTSYNCHRONIZEDBRACKET());
    } else if (SwitchStatement.class == parent.getClass()) {
      // switch文のときには，ここにくるのはswitch文内部のシンプルブロックのはず
      this.addToPeekModule(left ? new LEFTSIMPLEBLOCKBRACKET() : new RIGHTSIMPLEBLOCKBRACKET());
    } else if (TryStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTTRYBRACKET() : new RIGHTTRYBRACKET());
    } else if (CatchClause.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTCATCHCLAUSEBRACKET() : new RIGHTCATCHCLAUSEBRACKET());
    } else if (WhileStatement.class == parent.getClass()) {
      this.addToPeekModule(left ? new LEFTWHILEBRACKET() : new RIGHTWHILEBRACKET());
    } else if (LabeledStatement.class == parent.getClass()) {
      // ラベル文のときには，ここにくるのはラベル文の文の部分がシンプルブロックのはず
      this.addToPeekModule(left ? new LEFTSIMPLEBLOCKBRACKET() : new RIGHTSIMPLEBLOCKBRACKET());
    } else {
      System.err.println("unexpected parent type: " + parent.getClass()
          .getName());
    }
  }

  @Override
  public boolean visit(final BlockComment node) {
    this.addToPeekModule(new BLOCKCOMMENT(node.toString()));
    return false;
  }

  @Override
  public boolean visit(final BooleanLiteral node) {
    this.addToPeekModule(BooleanLiteralFactory.create(node.toString()));
    return false;
  }

  @Override
  public boolean visit(final BreakStatement node) {

    this.addToPeekModule(new BREAK());

    final SimpleName label = node.getLabel();
    if (null != label) {
      this.contexts.push(LABELNAME.class);
      label.accept(this);
      final Class<?> context = this.contexts.pop();
      assert LABELNAME.class == context : "error happend at visit(BreakStatement)";
    }

    this.addToPeekModule(new BREAKSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final CastExpression node) {

    this.addToPeekModule(new LEFTCASTPAREN());

    node.getType()
        .accept(this);

    this.addToPeekModule(new RIGHTCASTPAREN());

    node.getExpression()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final CatchClause node) {

    this.addToPeekModule(new CATCH(), new LEFTCATCHCLAUSEPAREN());

    node.getException()
        .accept(this);

    this.addToPeekModule(new RIGHTCATCHCLAUSEPAREN());

    node.getBody()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final CharacterLiteral node) {

    final String literal = node.getEscapedValue();
    this.addToPeekModule(new CHARLITERAL(literal));

    return false;
  }

  @Override
  public boolean visit(final ClassInstanceCreation node) {

    final Expression expression = node.getExpression();
    if (null != expression) {
      expression.accept(this);
      this.addToPeekModule(new DOT());
    }

    this.addToPeekModule(new NEW());

    node.getType()
        .accept(this);

    this.addToPeekModule(new LEFTCLASSINSTANCECREATIONPAREN());

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {
      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new CLASSINSTANCECREATIONCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTCLASSINSTANCECREATIONPAREN());

    final AnonymousClassDeclaration acd = node.getAnonymousClassDeclaration();
    if (null != acd) {
      acd.accept(this);
    }

    return false;
  }

  // 変更の必要なし
  @Override
  public boolean visit(final CompilationUnit node) {
    return super.visit(node);
  }

  @Override
  public boolean visit(final ConditionalExpression node) {

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new QUESTION());

    node.getThenExpression()
        .accept(this);

    this.addToPeekModule(new COLON());

    node.getElseExpression()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final ConstructorInvocation node) {

    this.addToPeekModule(new THIS(), new LEFTCONSTRUCTORINVOCATIONPAREN());

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {
      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new CONSTRUCTORINVOCATIONCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTCONSTRUCTORINVOCATIONPAREN(),
        new CONSTRUCTORINVOCATIONSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final ContinueStatement node) {

    this.addToPeekModule(new CONTINUE());

    final SimpleName label = node.getLabel();
    if (null != label) {
      this.contexts.push(LABELNAME.class);
      label.accept(this);
      final Class<?> context = this.contexts.pop();
      assert LABELNAME.class == context : "error happend at visit(ContinueStatement)";
    }

    this.addToPeekModule(new CONTINUESTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final CreationReference node) {

    node.getType()
        .accept(this);

    this.addToPeekModule(new METHODREFERENCE(), new NEW());

    return false;
  }

  @Override
  public boolean visit(final Dimension node) {

    this.addToPeekModule(new LEFTSQUAREBRACKET());

    final List<?> annotations = node.annotations();
    if (null != annotations && !annotations.isEmpty()) {
      ((Annotation) annotations.get(0)).accept(this);
      for (int index = 1; index < annotations.size(); index++) {
        this.addToPeekModule(new DIMENSIONCOMMA());
        ((Annotation) annotations.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTSQUAREBRACKET());

    return false;
  }

  @Override
  public boolean visit(final DoStatement node) {

    this.addToPeekModule(new DO());

    node.getBody()
        .accept(this);

    this.addToPeekModule(new WHILE(), new LEFTDOPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTDOPAREN(), new DOSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final EmptyStatement node) {
    this.addToPeekModule(new EMPTYSTATEMENTSEMICOLON());
    return false;
  }

  @Override
  public boolean visit(final EnhancedForStatement node) {

    this.addToPeekModule(new FOR(), new LEFTENHANCEDFORPAREN());

    node.getParameter()
        .accept(this);

    this.addToPeekModule(new COLON());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTENHANCEDFORPAREN());

    node.getBody()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final EnumConstantDeclaration node) {

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    this.contexts.push(CLASSNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert CLASSNAME.class == context : "error happend at JavaFileVisitor#visit(EnumConstantDeclaration)";

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {

      this.addToPeekModule(new LEFTENUMPAREN());

      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new ENUMCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }

      this.addToPeekModule(new RIGHTENUMPAREN());
    }

    final AnonymousClassDeclaration acd = node.getAnonymousClassDeclaration();
    if (acd != null) {
      acd.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final EnumDeclaration node) {

    // インナークラスでない場合は，新しいクラスモジュールを作り，モジュールスタックにpush
    if (0 == this.classNestLevel) {
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final String className = node.getName()
          .getIdentifier();
      final FinerJavaClass classModule = new FinerJavaClass(className, outerModule, this.config);
      this.moduleStack.push(classModule);
      this.moduleList.add(classModule);
    }

    this.classNestLevel++;

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    // "class"の処理
    this.addToPeekModule(new ENUM());

    this.contexts.push(CLASSNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert CLASSNAME.class == context : "error happend at JavaFileVisitor#visit(EnumDeclaration)";

    this.addToPeekModule(new LEFTCLASSBRACKET());

    for (final Object o : node.bodyDeclarations()) {
      final BodyDeclaration body = (BodyDeclaration) o;
      body.accept(this);
    }

    this.addToPeekModule(new RIGHTCLASSBRACKET());

    this.classNestLevel--;

    // インナークラスでない場合は，モジュールスタックからクラスモジュールをポップし，外側のモジュールにクラスを表すトークンを追加する
    if (0 == this.classNestLevel) {
      final FinerJavaClass finerJavaClass = (FinerJavaClass) this.moduleStack.pop();
      this.addToPeekModule(
          new FinerJavaClassToken("ClassToken[" + finerJavaClass.name + "]", finerJavaClass));
    }

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final ExportsDirective node) {
    log.error("JavaFileVisitor#visit(ExportsDirective) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final ExpressionMethodReference node) {

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new METHODREFERENCE());

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == context : "error happened at visit(ExpressionMethodReference)";

    return false;
  }

  @Override
  public boolean visit(final ExpressionStatement node) {

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new EXPRESSIONSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final FieldAccess node) {

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new DOT());

    this.contexts.push(VARIABLENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert VARIABLENAME.class == context : "error happened at visit(FieldAccess)";

    return false;
  }

  @Override
  public boolean visit(final FieldDeclaration node) {

    // 内部クラスのフィールドでない場合は，ダミーフィールドを生成し，モジュールスタックに追加
    if (1 == this.classNestLevel) {
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final FinerJavaField dummyField = new FinerJavaField("DummyField", outerModule, null);
      this.moduleStack.push(dummyField);
    }

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    // 型の処理
    node.getType()
        .accept(this);

    // フィールド名の処理
    final List<?> fragments = node.fragments();
    ((VariableDeclarationFragment) fragments.get(0)).accept(this);
    for (int index = 1; index < fragments.size(); index++) {
      this.addToPeekModule(new FIELDDECLARATIONCOMMA());
      ((VariableDeclarationFragment) fragments.get(index)).accept(this);
    }

    // フィールド宣言の最後にあるセミコロンの処理
    this.addToPeekModule(new FIELDDECLARATIONSEMICOLON());

    // フィールドモジュールの名前を生成
    final StringBuilder fieldFileName = new StringBuilder();
    if (this.config.isAccessModifierIncluded()) { // アクセス修飾子を名前に入れる場合
      final int modifiers = node.getModifiers();
      if (Modifier.isPublic(modifiers)) {
        fieldFileName.append("public_");
      } else if (Modifier.isProtected(modifiers)) {
        fieldFileName.append("protected_");
      } else if (Modifier.isPrivate(modifiers)) {
        fieldFileName.append("private_");
      }
    }
    final String type = node.getType()
        .toString()
        .replace(' ', '-') // avoiding space existences
        .replace('?', '#') // for window's file system
        .replace('<', '[') // for window's file system
        .replace('>', ']'); // for window's file system
    fieldFileName.append(type);
    fieldFileName.append("_");
    fieldFileName.append(((VariableDeclarationFragment) fragments.get(0)).getName());
    for (int index = 1; index < fragments.size(); index++) {
      fieldFileName.append("_");
      fieldFileName.append(((VariableDeclarationFragment) fragments.get(index)).getName());
    }

    // 内部クラスのフィールドでない場合は，ダミーフィールドをスタックから取り除く
    if (1 == this.classNestLevel) {
      final FinerJavaModule dummyField = this.moduleStack.pop();
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final FinerJavaField javaField =
          new FinerJavaField(fieldFileName.toString(), outerModule, this.config);
      this.moduleList.add(javaField);

      // 一行一トークンの場合は，ダミーフィールド内のトークンを抽出し，methodModule に移行
      if (this.config.isTokenized()) {
        dummyField.getTokens()
            .forEach(javaField::addToken);
        this.addToPeekModule(
            new FinerJavaFieldToken("FieldToken[" + javaField.name + "]", javaField));
      }

      // 一行一トークンでない場合は，,フィールドの文字列表現からトークンを作り出し，それらをフィールドモジュールに追加し，処理を終了する
      else {
        Stream.of(node.toString()
            .split("(\\r\\n|\\r|\\n)"))
            .map(l -> new LineToken(l))
            .forEach(javaField::addToken);
      }
    }

    return false;
  }

  @Override
  public boolean visit(final ForStatement node) {

    this.addToPeekModule(new FOR(), new LEFTFORPAREN());

    // 初期化子の処理
    final List<?> initializers = node.initializers();
    if (null != initializers && !initializers.isEmpty()) {
      ((Expression) initializers.get(0)).accept(this);
      for (int index = 1; index < initializers.size(); index++) {
        this.addToPeekModule(new FORINITIALIZERCOMMA());
        ((Expression) initializers.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new FORINITIALIZERSEMICOLON());

    // 条件節の処理
    final Expression condition = node.getExpression();
    if (null != condition) {
      condition.accept(this);
    }

    this.addToPeekModule(new FORCONDITIONSEMICOLON());

    // 更新子の処理
    final List<?> updaters = node.updaters();
    if (null != updaters && !updaters.isEmpty()) {
      ((Expression) updaters.get(0)).accept(this);
      for (int index = 1; index < updaters.size(); index++) {
        this.addToPeekModule(new FORUPDATERCOMMA());
        ((Expression) updaters.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTFORPAREN());

    final Statement body = node.getBody();
    if (null != body) {
      body.accept(this);
    }

    return false;

  }

  @Override
  public boolean visit(final IfStatement node) {

    this.addToPeekModule(new IF(), new LEFTIFPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTIFPAREN());

    final Statement thenStatement = node.getThenStatement();
    if (null != thenStatement) {
      thenStatement.accept(this);
    }

    final Statement elseStatement = node.getElseStatement();
    if (null != elseStatement) {
      this.addToPeekModule(new ELSE());
      elseStatement.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final ImportDeclaration node) {

    if (node.isStatic()) {
      this.addToPeekModule(new STATIC());
    }

    this.addToPeekModule(new IMPORT());

    this.contexts.push(IMPORTNAME.class);
    node.getName()
        .accept(this);
    final Class<?> c = this.contexts.pop();
    assert c == IMPORTNAME.class : "context error.";

    return false;
  }

  @Override
  public boolean visit(final InfixExpression node) {

    node.getLeftOperand()
        .accept(this);

    final Operator operator = node.getOperator();
    final JavaToken operatorToken = OperatorFactory.create(operator.toString());
    this.addToPeekModule(operatorToken);

    node.getRightOperand()
        .accept(this);

    final List<?> extendedOperands = node.extendedOperands();
    for (int index = 0; index < extendedOperands.size(); index++) {
      this.addToPeekModule(operatorToken);
      ((Expression) extendedOperands.get(index)).accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final Initializer node) {

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    node.getBody()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final InstanceofExpression node) {

    node.getLeftOperand()
        .accept(this);

    this.addToPeekModule(new INSTANCEOF());

    node.getRightOperand()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final IntersectionType node) {

    final List<?> types = node.types();
    ((Type) types.get(0)).accept(this);

    for (int index = 1; index < types.size(); index++) {
      this.addToPeekModule(new AND());
      ((Type) types.get(index)).accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final Javadoc node) {
    this.addToPeekModule(new JAVADOCCOMMENT(this.removeTerminalLineCharacter(node.toString())));
    return false;
  }

  @Override
  public boolean visit(final LabeledStatement node) {

    this.contexts.push(LABELNAME.class);
    node.getLabel()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert LABELNAME.class == context : "error happened at JavaFileVisitor#visit(LabeledStatement)";

    this.addToPeekModule(new COLON());

    node.getBody()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final LambdaExpression node) {

    if (node.hasParentheses()) {
      this.addToPeekModule(new LEFTLAMBDAEXPRESSIONPAREN());
    }

    final List<?> parameters = node.parameters();
    if (null != parameters && !parameters.isEmpty()) {
      ((VariableDeclaration) parameters.get(0)).accept(this);
      for (int index = 1; index < parameters.size(); index++) {
        this.addToPeekModule(new LAMBDAEXPRESSIONCOMMA());
        ((VariableDeclaration) parameters.get(index)).accept(this);
      }
    }

    if (node.hasParentheses()) {
      this.addToPeekModule(new RIGHTLAMBDAEXPRESSIONPAREN());
    }

    this.addToPeekModule(new RIGHTARROW());

    node.getBody()
        .accept(this);

    return false;
  }

  @Override
  public boolean visit(final LineComment node) {
    this.addToPeekModule(new LINECOMMENT(node.toString()));
    return false;
  }

  @Override
  public boolean visit(final MarkerAnnotation node) {
    this.addToPeekModule(new ANNOTATION(node.toString()));
    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final MemberRef node) {
    log.error("JavaFileVisitor#visit(MemberRef) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final MemberValuePair node) {
    final String variableName = node.getName()
        .getIdentifier();
    this.addToPeekModule(new VARIABLENAME(variableName), new ASSIGN());

    node.getValue()
        .accept(this);

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final MethodRef node) {
    log.error("JavaFileVisitor#visit(MemberRef) is not implemented yet.");
    return super.visit(node);
  }

  // TODO テストできていない
  @Override
  public boolean visit(final MethodRefParameter node) {
    log.error("JavaFileVisitor#visit(MethodRefParameter) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final MethodDeclaration node) {

    // 内部クラスのメソッドでない場合は，ダミーメソッドを生成し，モジュールスタックに追加
    if (1 == this.classNestLevel) {
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final FinerJavaMethod dummyMethod = new FinerJavaMethod("DummyMethod", outerModule, null);
      this.moduleStack.push(dummyMethod);
    }

    // Javadoc コメントの処理
    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理（ダミーメソッドに追加）
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    // Method Type Erasure の処理
    @SuppressWarnings("rawtypes")
    final List typeParameters = node.typeParameters();
    if (null != typeParameters && !typeParameters.isEmpty()) {
      this.addToPeekModule(new LESS());
      ((TypeParameter) typeParameters.get(0)).accept(this);
      for (int index = 1; index < typeParameters.size(); index++) {
        this.addToPeekModule(new METHODDECLARAIONPARAMETERCOMMA());
        ((TypeParameter) typeParameters.get(index)).accept(this);
      }
      this.addToPeekModule(new GREAT());
    }

    // 返り値の処理（ダミーメソッドに追加）
    final Type returnType = node.getReturnType2();
    if (null != returnType) { // コンストラクタのときは returnType が null
      this.contexts.push(TYPENAME.class);
      returnType.accept(this);
      final Class<?> context = this.contexts.pop();
      assert TYPENAME.class == context : "error happend at visit(MethodDeclaration)";
    }

    {// メソッド名の処理（ダミーメソッドに追加）
      this.contexts.push(DECLAREDMETHODNAME.class);
      node.getName()
          .accept(this);
      final Class<?> context = this.contexts.pop();
      assert DECLAREDMETHODNAME.class == context : "error happend at visit(MethodDeclaration)";
    }

    // "(" の処理（ダミーメソッドに追加）
    this.addToPeekModule(new LEFTMETHODPAREN());

    // 引数の処理（ダミーメソッドに追加）
    final List<?> parameters = node.parameters();
    if (null != parameters && !parameters.isEmpty()) {
      ((SingleVariableDeclaration) parameters.get(0)).accept(this);
      for (int index = 1; index < parameters.size(); index++) {
        this.addToPeekModule(new METHODDECLARAIONPARAMETERCOMMA());
        ((SingleVariableDeclaration) parameters.get(index)).accept(this);
      }
    }

    // ")" の処理（ダミーメソッドに追加）
    this.addToPeekModule(new RIGHTMETHODPAREN());

    // throws 節の処理
    final List<?> exceptions = node.thrownExceptionTypes();
    if (null != exceptions && !exceptions.isEmpty()) {
      this.addToPeekModule(new THROWS());
      this.contexts.push(TYPENAME.class);
      ((Type) exceptions.get(0)).accept(this);
      for (int index = 1; index < exceptions.size(); index++) {
        this.addToPeekModule(new METHODDECLARATIONTHROWSCOMMA());
        ((Type) exceptions.get(index)).accept(this);
      }
      final Class<?> context = this.contexts.pop();
      assert TYPENAME.class == context : "error happened at visit(MethodDeclaration)";
    }

    // メソッドモジュールの名前を生成
    final StringBuilder methodFileName = new StringBuilder();
    if (this.config.isAccessModifierIncluded()) { // アクセス修飾子を名前に入れる場合
      final int modifiers = node.getModifiers();
      if (Modifier.isPublic(modifiers)) {
        methodFileName.append("public_");
      } else if (Modifier.isProtected(modifiers)) {
        methodFileName.append("protected_");
      } else if (Modifier.isPrivate(modifiers)) {
        methodFileName.append("private_");
      }
    }
    if (this.config.isMethodTypeErasureIncluded()) { // Erasure を名前に入れる場合
      if (null != typeParameters && !typeParameters.isEmpty()) {
        methodFileName.append("[");
        final List<String> erasures = new ArrayList<>();
        for (final Object o : node.typeParameters()) {
          final TypeParameter typeParameter = (TypeParameter) o;
          final String type = typeParameter.toString()
              .replace(' ', '-') // avoiding space existences
              .replace('?', '#') // for window's file system
              .replace('<', '[') // for window's file system
              .replace('>', ']'); // for window's file system
          erasures.add(type);
        }
        methodFileName.append(String.join(",", erasures));
        methodFileName.append("]_");
      }
    }
    if (this.config.isReturnTypeIncluded()) { // 返り値の型を名前に入れる場合
      if (null != returnType) {
        final String type = returnType.toString()
            .replace(' ', '-') // avoiding space existences
            .replace('?', '#') // for window's file system
            .replace('<', '[') // for window's file system
            .replace('>', ']'); // for window's file system
        methodFileName.append(type);
        methodFileName.append("_");
      }
    }
    final String methodName = node.getName()
        .getIdentifier();
    methodFileName.append(methodName);
    methodFileName.append("(");
    final List<String> types = new ArrayList<>();
    for (final Object parameter : node.parameters()) {
      final SingleVariableDeclaration svd = (SingleVariableDeclaration) parameter;
      final StringBuilder typeText = new StringBuilder();
      typeText.append(svd.getType());
      for (int i = 0; i < svd.getExtraDimensions(); i++) { // "int a[]"のような表記に対応するため
        typeText.append("[]");
      }
      if (svd.isVarargs()) {
        typeText.append("...");
      }
      final String type = typeText.toString()
          .replace(' ', '-') // avoiding space existences
          .replace('?', '#') // for window's file system
          .replace('<', '[') // for window's file system
          .replace('>', ']'); // for window's file system
      types.add(type);
    }
    methodFileName.append(String.join(",", types));
    methodFileName.append(")");

    // 内部クラスのメソッドでない場合は，ダミーメソッドをスタックから取り除く
    if (1 == this.classNestLevel) {
      final FinerJavaModule dummyMethod = this.moduleStack.pop();
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final FinerJavaMethod javaMethod =
          new FinerJavaMethod(methodFileName.toString(), outerModule, this.config);
      this.moduleStack.push(javaMethod);
      this.moduleList.add(javaMethod);

      // 一行一トークンの場合は，ダミーメソッド内のトークンを抽出し，methodModule に移行
      if (this.config.isTokenized()) {
        dummyMethod.getTokens()
            .forEach(javaMethod::addToken);
      }

      // 一行一トークンでない場合は，メソッドの文字列表現からトークンを作り出し，それらをメソッドモジュールに追加し，処理を終了する
      else {
        Stream.of(node.toString()
            .split("(\\r\\n|\\r|\\n)"))
            .map(l -> new LineToken(l))
            .forEach(javaMethod::addToken);
        this.moduleStack.pop();
        return false;
      }
    }

    // メソッドの中身の処理
    final Block body = node.getBody();
    if (null != body) {
      body.accept(this);
    } else {
      this.addToPeekModule(new METHODDECLARATIONSEMICOLON());
    }

    // 内部クラス内のメソッドではない場合は，メソッドモジュールをスタックから取り出す
    if (1 == this.classNestLevel) {
      final FinerJavaMethod finerJavaMethod = (FinerJavaMethod) this.moduleStack.pop();
      this.addToPeekModule(
          new FinerJavaMethodToken("MetodToken[" + finerJavaMethod.name + "]", finerJavaMethod));
    }

    return false;
  }

  @Override
  public boolean visit(final MethodInvocation node) {

    final Expression qualifier = node.getExpression();
    if (null != qualifier) {
      qualifier.accept(this);
      this.addToPeekModule(new DOT());
    }

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == context : "error happened at visit(MethodInvocation)";

    this.addToPeekModule(new LEFTMETHODINVOCATIONPAREN());

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {
      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new METHODINVOCATIONCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTMETHODINVOCATIONPAREN());

    return false;
  }

  // 変更の必要なし
  @Override
  public boolean visit(final Modifier node) {
    return super.visit(node);
  }

  // TODO テストできていない
  @Override
  public boolean visit(final ModuleDeclaration node) {
    log.error("JavaFileVisitor#visit(ModuleDeclaration) is not implemented yet.");
    return super.visit(node);
  }

  // TODO テストできていない
  @Override
  public boolean visit(final ModuleModifier node) {
    log.error("JavaFileVisitor#visit(ModuleModifier) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final NameQualifiedType node) {

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getQualifier()
        .accept(this);
    final Class<?> qualifierText = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == qualifierText : "error happened at visit(NameQualifiedType)";

    this.addToPeekModule(new DOT());

    for (final Object o : node.annotations()) {
      final Annotation annotation = (Annotation) o;
      annotation.accept(this);
    }

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);
    final Class<?> nameContext = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == nameContext : "error happened at visit(NameQualifiedType)";

    return false;
  }

  @Override
  public boolean visit(final NormalAnnotation node) {

    final String annotationName = "@" + node.getTypeName();
    this.addToPeekModule(new ANNOTATION(annotationName), new LEFTANNOTATIONPAREN());

    @SuppressWarnings("unchecked")
    final List<MemberValuePair> nodes = node.values();
    if (null != nodes && !nodes.isEmpty()) {
      nodes.get(0)
          .accept(this);
      for (int index = 1; index < nodes.size(); index++) {
        this.addToPeekModule(new ANNOTATIONCOMMA());
        nodes.get(index)
            .accept(this);
      }
    }

    this.addToPeekModule(new RIGHTANNOTATIONPAREN());

    return false;
  }

  @Override
  public boolean visit(final NullLiteral node) {
    this.addToPeekModule(new NULL());
    return false;
  }

  @Override
  public boolean visit(final NumberLiteral node) {
    this.addToPeekModule(new NUMBERLITERAL(node.getToken()));
    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final OpensDirective node) {
    log.error("JavaFileVisitor#visit(OpensDirective) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final PackageDeclaration node) {

    this.addToPeekModule(new PACKAGE());

    this.contexts.push(PACKAGENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert PACKAGENAME.class == context : "context error at JavaFileVisitor#visit(PackageDeclaration)";

    return false;
  }

  @Override
  public boolean visit(final ParameterizedType node) {

    node.getType()
        .accept(this);

    this.addToPeekModule(new LESS());

    final List<?> typeArguments = node.typeArguments();
    if (null != typeArguments && !typeArguments.isEmpty()) {
      ((Type) typeArguments.get(0)).accept(this);
      for (int index = 1; index < typeArguments.size(); index++) {
        this.addToPeekModule(new PARAMETERIZEDTYPECOMMA());
        ((Type) typeArguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new GREAT());

    return false;
  }

  @Override
  public boolean visit(final ParenthesizedExpression node) {

    this.addToPeekModule(new LEFTPARENTHESIZEDEXPRESSIONPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTPARENTHESIZEDEXPRESSIONPAREN());

    return false;
  }

  @Override
  public boolean visit(final PostfixExpression node) {

    node.getOperand()
        .accept(this);

    final PostfixExpression.Operator operator = node.getOperator();
    final JavaToken operatorToken = OperatorFactory.create(operator.toString());
    this.addToPeekModule(operatorToken);

    return false;
  }

  @Override
  public boolean visit(final PrefixExpression node) {

    final PrefixExpression.Operator operator = node.getOperator();
    final JavaToken operatorToken = OperatorFactory.create(operator.toString());
    this.addToPeekModule(operatorToken);

    node.getOperand()
        .accept(this);

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final ProvidesDirective node) {
    log.error("JavaFileVisitor#visit(ProvidesDirective) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final PrimitiveType node) {

    final JavaToken primitiveTypeToken = PrimitiveTypeFactory.create(node.getPrimitiveTypeCode()
        .toString());
    this.addToPeekModule(primitiveTypeToken);

    return super.visit(node);
  }

  @Override
  public boolean visit(final QualifiedName node) {

    final Name qualifier = node.getQualifier();
    qualifier.accept(this);

    this.addToPeekModule(new DOT());

    final SimpleName name = node.getName();
    name.accept(this);

    return false;
  }

  @Override
  public boolean visit(final QualifiedType node) {

    node.getQualifier()
        .accept(this);

    this.addToPeekModule(new DOT());

    for (final Object o : node.annotations()) {
      final Annotation annotation = (Annotation) o;
      annotation.accept(this);
    }

    this.contexts.push(TYPENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert TYPENAME.class == context : "error happened at JavaFileVisitor#visit(QualifiedType)";

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final RequiresDirective node) {
    log.error("JavaFileVisitor#visit(RequiresDirective) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final ReturnStatement node) {

    this.addToPeekModule(new RETURN());

    final Expression expression = node.getExpression();
    if (null != expression) {
      expression.accept(this);
    }

    this.addToPeekModule(new RETURNSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final SimpleName node) {

    final String identifier = node.getIdentifier();

    if (this.contexts.isEmpty()) {
      this.addToPeekModule(new VARIABLENAME(identifier));
      return false;
    }

    final Class<?> context = this.contexts.peek();
    if (VARIABLENAME.class == context) {
      this.addToPeekModule(new VARIABLENAME(identifier));
    }

    else if (TYPENAME.class == context) {
      this.addToPeekModule(new TYPENAME(identifier));
    }

    else if (TYPEPARAMETERNAME.class == context) {
      this.addToPeekModule(new TYPEPARAMETERNAME(identifier));
    }

    else if (DECLAREDMETHODNAME.class == context) {
      this.addToPeekModule(new DECLAREDMETHODNAME(identifier));
    }

    else if (INVOKEDMETHODNAME.class == context) {
      this.addToPeekModule(new INVOKEDMETHODNAME(identifier));
    }

    else if (PACKAGENAME.class == context) {
      this.addToPeekModule(new PACKAGENAME(identifier));
    }

    else if (IMPORTNAME.class == context) {
      this.addToPeekModule(new IMPORTNAME(identifier));
    }

    else if (CLASSNAME.class == context) {
      this.addToPeekModule(new CLASSNAME(identifier));
    }

    else if (LABELNAME.class == context) {
      this.addToPeekModule(new LABELNAME(identifier));
    }

    else {
      log.error("unknown context: " + context.toString());
    }

    return false;
  }

  @Override
  public boolean visit(final SimpleType node) {
    this.contexts.push(TYPENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert TYPENAME.class == context : "error happend at visit(SimpleType)";

    return false;
  }

  @Override
  public boolean visit(final SingleMemberAnnotation node) {

    this.addToPeekModule(new ANNOTATION(node.toString()));
    return false;
  }

  @Override
  public boolean visit(final SingleVariableDeclaration node) {

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    // 型の処理
    node.getType()
        .accept(this);

    // 可変長引数なら"..."を追加
    if (node.isVarargs()) {
      this.addToPeekModule(new VariableArity());
    }

    {// 変数名の処理
      this.contexts.push(VARIABLENAME.class);
      node.getName()
          .accept(this);
      final Class<?> context = this.contexts.pop();
      assert VARIABLENAME.class == context : "error happend at visit(SingleVariableDeclaration";
    }

    return false;
  }

  @Override
  public boolean visit(final StringLiteral node) {
    final String literal = node.getEscapedValue();
    this.addToPeekModule(new STRINGLITERAL(literal));
    return false;
  }

  @Override
  public boolean visit(final SuperConstructorInvocation node) {

    final Expression qualifier = node.getExpression();
    if (null != qualifier) {
      qualifier.accept(this);
      this.addToPeekModule(new DOT());
    }

    this.addToPeekModule(new SUPER(), new LEFTSUPERCONSTRUCTORINVOCATIONPAREN());

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {
      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new SUPERCONSTRUCTORINVOCATIONCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTSUPERCONSTRUCTORINVOCATIONPAREN(),
        new SUPERCONSTRUCTORINVOCATIONSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final SuperFieldAccess node) {

    this.addToPeekModule(new SUPER(), new DOT());

    this.contexts.push(VARIABLENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert VARIABLENAME.class == context : "error happend at visit(SuperFieldAccess";

    return false;
  }

  // TODO node.getQualifier が null でない場合はテストできていない
  @Override
  public boolean visit(final SuperMethodInvocation node) {

    final Name qualifier = node.getQualifier();
    if (null != qualifier) {
      qualifier.accept(this);
      this.addToPeekModule(new DOT());
    }

    this.addToPeekModule(new SUPER(), new DOT());

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);

    final Class<?> context = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == context : "error happend at JavaFileVisitor#visit(SuperMethodInvocation)";

    this.addToPeekModule(new LEFTMETHODINVOCATIONPAREN());

    final List<?> arguments = node.arguments();
    if (null != arguments && !arguments.isEmpty()) {
      ((Expression) arguments.get(0)).accept(this);
      for (int index = 1; index < arguments.size(); index++) {
        this.addToPeekModule(new METHODINVOCATIONCOMMA());
        ((Expression) arguments.get(index)).accept(this);
      }
    }

    this.addToPeekModule(new RIGHTMETHODINVOCATIONPAREN());

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final SuperMethodReference node) {

    this.addToPeekModule(new SUPER(), new METHODREFERENCE());

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == context : "error happened at visit(SuperMethodReference)";

    return false;
  }

  @Override
  public boolean visit(final SwitchCase node) {

    final Expression expression = node.getExpression();

    // case のとき
    if (null != expression) {
      this.addToPeekModule(new CASE());
      expression.accept(this);
    }

    // default のとき
    else {
      this.addToPeekModule(new DEFAULT());
    }

    this.addToPeekModule(new COLON());

    return false;
  }

  @Override
  public boolean visit(final SwitchStatement node) {

    this.addToPeekModule(new SWITCH(), new LEFTSWITCHPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTSWITCHPAREN(), new LEFTSWITCHBRACKET());

    for (final Object o : node.statements()) {
      final Statement statement = (Statement) o;
      statement.accept(this);
    }

    this.addToPeekModule(new RIGHTSWITCHBRACKET());

    return false;
  }

  @Override
  public boolean visit(final SynchronizedStatement node) {

    this.addToPeekModule(new SYNCHRONIZED(), new LEFTSYNCHRONIZEDPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTSYNCHRONIZEDPAREN());

    node.getBody()
        .accept(this);

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final TagElement node) {
    log.error("JavaFileVisitor#visit(TagElement) is not implemented yet.");
    return super.visit(node);
  }

  // TODO テストできていない
  @Override
  public boolean visit(final TextElement node) {
    log.error("JavaFileVisitor#visit(TextElement) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final ThisExpression node) {
    this.addToPeekModule(new THIS());
    return false;
  }

  @Override
  public boolean visit(final ThrowStatement node) {

    this.addToPeekModule(new THROW());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new THROWSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final TryStatement node) {

    this.addToPeekModule(new TRY());

    final List<?> resources = node.resources();
    if (null != resources && !resources.isEmpty()) {
      this.addToPeekModule(new LEFTTRYPAREN());

      ((Expression) resources.get(0)).accept(this);

      for (int index = 1; index < resources.size(); index++) {
        this.addToPeekModule(new TRYRESOURCESEMICOLON());
        ((Expression) resources.get(index)).accept(this);
      }

      this.addToPeekModule(new RIGHTTRYPAREN());
    }

    node.getBody()
        .accept(this);

    final List<?> catchClauses = node.catchClauses();
    for (final Object o : catchClauses) {
      final CatchClause catchClause = (CatchClause) o;
      catchClause.accept(this);
    }

    final Block finallyBlock = node.getFinally();
    if (null != finallyBlock) {
      this.addToPeekModule(new FINALLY());
      finallyBlock.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final TypeDeclaration node) {

    // インナークラスでない場合は，新しいクラスモジュールを作り，モジュールスタックにpush
    if (0 == this.classNestLevel) {
      final FinerJavaModule outerModule = this.moduleStack.peek();
      final String className = node.getName()
          .getIdentifier();
      final FinerJavaClass classModule = new FinerJavaClass(className, outerModule, this.config);
      this.moduleStack.push(classModule);
      this.moduleList.add(classModule);
    }

    this.classNestLevel++;

    final Javadoc javadoc = node.getJavadoc();
    if (null != javadoc) {
      this.addToPeekModule(
          new JAVADOCCOMMENT(this.removeTerminalLineCharacter(javadoc.toString())));
    }

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    // "class"の処理
    this.addToPeekModule(new CLASS());

    // クラス名の処理
    this.contexts.push(CLASSNAME.class);
    node.getName()
        .accept(this);
    final Class<?> nameContext = this.contexts.pop();
    assert CLASSNAME.class == nameContext : "error happened at visit(TypeDeclaration)";

    // extends 節の処理
    final Type superType = node.getSuperclassType();
    if (null != superType) {
      this.addToPeekModule(new EXTENDS());
      this.contexts.push(TYPENAME.class);
      superType.accept(this);
      final Class<?> extendsContext = this.contexts.pop();
      assert TYPENAME.class == extendsContext : "error happened at visit(TypeDeclaration)";
    }

    // implements 節の処理
    @SuppressWarnings("rawtypes")
    final List interfaces = node.superInterfaceTypes();
    if (null != interfaces && 0 < interfaces.size()) {

      this.contexts.push(TYPENAME.class);

      this.addToPeekModule(new IMPLEMENTS());
      ((Type) interfaces.get(0)).accept(this);

      for (int index = 1; index < interfaces.size(); index++) {
        this.addToPeekModule(new TYPEDECLARATIONCOMMA());
        ((Type) interfaces.get(index)).accept(this);
      }

      final Class<?> implementsContext = this.contexts.pop();
      assert TYPENAME.class == implementsContext : "error happened at visit(TypeDeclaration)";
    }

    this.addToPeekModule(new LEFTCLASSBRACKET());

    // 中身の処理
    for (final Object o : node.bodyDeclarations()) {
      final BodyDeclaration bodyDeclaration = (BodyDeclaration) o;
      bodyDeclaration.accept(this);
    }

    this.addToPeekModule(new RIGHTCLASSBRACKET());

    this.classNestLevel--;

    // インナークラスでない場合は，モジュールスタックからクラスモジュールをポップし，外側のモジュールにクラスを表すトークンを追加する
    if (0 == this.classNestLevel) {
      final FinerJavaClass finerJavaClass = (FinerJavaClass) this.moduleStack.pop();
      this.addToPeekModule(
          new FinerJavaClassToken("ClassToken[" + finerJavaClass.name + "]", finerJavaClass));
    }

    return false;
  }

  // 変更の必要なし
  @Override
  public boolean visit(final TypeDeclarationStatement node) {
    return super.visit(node);
  }

  @Override
  public boolean visit(final TypeLiteral node) {

    node.getType()
        .accept(this);

    this.addToPeekModule(new DOT(), new CLASS());

    return false;
  }

  @Override
  public boolean visit(final TypeMethodReference node) {

    node.getType()
        .accept(this);

    this.addToPeekModule(new METHODREFERENCE());

    this.contexts.push(INVOKEDMETHODNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert INVOKEDMETHODNAME.class == context : "error happened at visit(TypeMethodReference)";

    return super.visit(node);
  }

  // TODO テストできていない
  @Override
  public boolean visit(final TypeParameter node) {

    this.contexts.push(TYPEPARAMETERNAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert TYPEPARAMETERNAME.class == context : "error happened at visit(TypeParameter)";

    @SuppressWarnings("rawtypes")
    List typeBounds = node.typeBounds();
    if (null != typeBounds && !typeBounds.isEmpty()) {
      this.addToPeekModule(new EXTENDS());
      ((Type) typeBounds.get(0)).accept(this);
      for (int index = 1; index < typeBounds.size(); index++) {
        this.addToPeekModule(new AND());
        ((Type) typeBounds.get(index)).accept(this);
      }
    }

    return false;
  }

  @Override
  public boolean visit(final UnionType node) {

    final List<?> types = node.types();
    ((Type) types.get(0)).accept(this);

    for (int index = 1; index < types.size(); index++) {
      this.addToPeekModule(new OR());
      ((Type) types.get(index)).accept(this);
    }

    return false;
  }

  // TODO テストできていない
  @Override
  public boolean visit(final UsesDirective node) {
    log.error("JavaFileVisitor#visit(UsesDirective) is not implemented yet.");
    return super.visit(node);
  }

  @Override
  public boolean visit(final VariableDeclarationExpression node) {

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    node.getType()
        .accept(this);

    final List<?> fragments = node.fragments();
    ((VariableDeclarationFragment) fragments.get(0)).accept(this);
    for (int index = 1; index < fragments.size(); index++) {
      this.addToPeekModule(new VARIABLEDECLARATIONCOMMA());
      ((VariableDeclarationFragment) fragments.get(index)).accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final VariableDeclarationStatement node) {

    // 修飾子の処理
    for (final Object modifier : node.modifiers()) {
      final JavaToken modifierToken = ModifierFactory.create(modifier.toString());
      this.addToPeekModule(modifierToken);
    }

    node.getType()
        .accept(this);

    final List<?> fragments = node.fragments();
    ((VariableDeclarationFragment) fragments.get(0)).accept(this);
    for (int index = 1; index < fragments.size(); index++) {
      this.addToPeekModule(new VARIABLEDECLARATIONCOMMA());
      ((VariableDeclarationFragment) fragments.get(index)).accept(this);
    }

    this.addToPeekModule(new VARIABLEDECLARATIONSTATEMENTSEMICOLON());

    return false;
  }

  @Override
  public boolean visit(final VariableDeclarationFragment node) {

    this.contexts.push(VARIABLENAME.class);
    node.getName()
        .accept(this);
    final Class<?> context = this.contexts.pop();
    assert VARIABLENAME.class == context : "error happened at JavaFileVisitor#visit(VariableDeclarationFragment)";

    final Expression initializer = node.getInitializer();
    if (null != initializer) {
      this.addToPeekModule(new ASSIGN());
      initializer.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final WhileStatement node) {

    this.addToPeekModule(new WHILE(), new LEFTWHILEPAREN());

    node.getExpression()
        .accept(this);

    this.addToPeekModule(new RIGHTWHILEPAREN());

    final Statement body = node.getBody();
    if (null != body) {
      body.accept(this);
    }

    return false;
  }

  @Override
  public boolean visit(final WildcardType node) {
    this.addToPeekModule(new QUESTION());
    return super.visit(node);
  }

  private String removeTerminalLineCharacter(final String text) {
    if (text.endsWith("\r\n")) {
      return this.removeTerminalLineCharacter(text.substring(0, text.length() - 2));
    } else if (text.endsWith("\r") || text.endsWith("\n")) {
      return this.removeTerminalLineCharacter(text.substring(0, text.length() - 1));
    } else
      return text;
  }

  private void addToPeekModule(final JavaToken... tokens) {
    final FinerJavaModule peekModule = this.moduleStack.peek();
    Stream.of(tokens)
        .forEach(peekModule::addToken);
  }
}
